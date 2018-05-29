package fredboat.sentinel

import com.fredboat.sentinel.SentinelExchanges
import com.fredboat.sentinel.entities.*
import com.google.common.base.Function
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import fredboat.perms.IPermissionSet
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.AsyncRabbitTemplate
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.TimeUnit

@Component
class Sentinel(private val template: AsyncRabbitTemplate,
               private val blockingTemplate: RabbitTemplate) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(Sentinel::class.java)
    }

    val guildCache: LoadingCache<Long, RawGuild> = CacheBuilder
            .newBuilder()
            .recordStats()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build<Long, RawGuild>(
                    CacheLoader.from(Function {
                        // TODO We need to do something about this blocking
                        val result = blockingTemplate.convertSendAndReceive(SentinelExchanges.REQUESTS, GuildRequest(it!!))

                        if (result == null) {
                            log.warn("Requested guild $it but got null in response!")
                        }

                        return@Function result as? RawGuild
                    })
            )

    init {
        // Send a hello when we start so we get SentinelHellos in return
        sendAndForget(FredBoatHello())
    }

    fun sendAndForget(request: Any) {
        template.convertSendAndReceive<Any>(SentinelExchanges.REQUESTS, request)
    }

    fun <T> send(request: Any): Mono<T> = Mono.create<T> {
        template.convertSendAndReceive<T>(request)
                .addCallback(
                        { res ->
                            if (res != null) it.success(res) else it.success()
                        },
                        { exc -> it.error(exc) }
                )
    }

    fun getGuilds(shard: Shard): Flux<RawGuild> = Flux.create {
        val req = GuildsRequest(shard.id)
        template.convertSendAndReceive<GuildsResponse?>(req).addCallback(
                { res ->
                    if (res != null) {
                        res.guilds.forEach { g -> it.next(g) }
                        it.complete()
                    } else {
                        it.error(RuntimeException("Response was null"))
                    }
                },
                { exc -> it.error(exc) }
        )
    }

    fun getGuild(id: Long) = guildCache.get(id)!!

    fun sendMessage(channel: RawTextChannel, message: IMessage): Mono<SendMessageResponse> = Mono.create {
        val req = SendMessageRequest(channel.id, message)
        template.convertSendAndReceive<SendMessageResponse?>(SentinelExchanges.REQUESTS, req).addCallback(
                { res -> it.success(res) },
                { exc -> it.error(exc) }
        )
    }

    // TODO: Figure out how to route this. We don't know what Sentinel to contact!
    fun sendPrivateMessage(user: User, message: IMessage): Mono<Unit> = Mono.create {
        val req = SendPrivateMessageRequest(user.id, message)
        template.convertSendAndReceive<Unit>(SentinelExchanges.REQUESTS, req).addCallback(
                { _ -> it.success() },
                { exc -> it.error(exc) }
        )
    }

    fun editMessage(channel: TextChannel, messageId: Long, message: IMessage): Mono<Unit> = Mono.create {
        val req = EditMessageRequest(channel.id, messageId, message)
        template.convertSendAndReceive<Unit>(SentinelExchanges.REQUESTS, req).addCallback(
                { _ -> it.success() },
                { exc -> it.error(exc) }
        )
    }

    fun deleteMessages(channel: TextChannel, messages: List<Long>): Mono<Unit> = Mono.create<Unit> {
        val req = MessageDeleteRequest(channel.id, messages)
        template.convertSendAndReceive<Unit>(SentinelExchanges.REQUESTS, req).addCallback(
                { _ -> it.success() },
                { exc -> it.error(exc) }
        )
    }

    fun sendTyping(channel: RawTextChannel) {
        val req = SendTypingRequest(channel.id)
        template.convertSendAndReceive<Unit>(SentinelExchanges.REQUESTS, req).addCallback(
                {},
                { exc -> log.error("Failed sendTyping in channel {}", channel, exc) }
        )
    }

    private var cachedApplicationInfo: ApplicationInfo? = null
    fun getApplicationInfo(): ApplicationInfo {
        if (cachedApplicationInfo != null) return cachedApplicationInfo as ApplicationInfo

        cachedApplicationInfo = blockingTemplate.convertSendAndReceive(ApplicationInfoRequest()) as ApplicationInfo
        return cachedApplicationInfo!!
    }

    /* Permissions */

    private fun checkPermissions(member: Member?, role: Role?, permissions: IPermissionSet):
            Mono<PermissionCheckResponse> = Mono.create {

        val guild = member?.guild ?: role!!.guild

        val req = GuildPermissionRequest(guild.id, member?.id, role?.id, permissions.raw)
        template.convertSendAndReceive<PermissionCheckResponse>(SentinelExchanges.REQUESTS, req).addCallback(
                { r -> it.success(r) },
                { exc -> it.error(RuntimeException("Failed checking permissions in $guild", exc)) }
        )
    }

    // Role and member are mutually exclusive
    fun checkPermissions(member: Member, permissions: IPermissionSet) = checkPermissions(member, null, permissions)

    fun checkPermissions(role: Role, permissions: IPermissionSet) = checkPermissions(null, role, permissions)

    private fun checkPermissions(channel: Channel, member: Member?, role: Role?, permissions: IPermissionSet):
            Mono<PermissionCheckResponse> = Mono.create {

        val req = ChannelPermissionRequest(channel.id, member?.id, role?.id, permissions.raw)
        template.convertSendAndReceive<PermissionCheckResponse>(SentinelExchanges.REQUESTS, req).addCallback(
                { r -> it.success(r) },
                { exc -> it.error(RuntimeException("Failed checking permissions in $channel", exc)) }
        )
    }

    // Role and member are mutually exclusive
    fun checkPermissions(channel: Channel, member: Member, permissions: IPermissionSet) = checkPermissions(channel, member, null, permissions)

    fun checkPermissions(channel: Channel, role: Role, permissions: IPermissionSet) = checkPermissions(channel, null, role, permissions)

    /**
     * Takes [members] and maps them to their effective permissions.
     *
     * @throws [IllegalArgumentException] if any member is not of the [guild]
     */
    fun getPermissions(guild: Guild, members: List<Member>): Flux<Long> {
        val req = BulkGuildPermissionRequest(guild.id, members.map {
            if (it.guildId != guild.id) throw IllegalArgumentException("All members must be of the same guild")
            it.id
        })

        return Flux.create { sink ->
            template.convertSendAndReceive<BulkGuildPermissionResponse>(SentinelExchanges.REQUESTS, req).addCallback(
                    { r ->
                        r!!.effectivePermissions.forEach { sink.next(it ?: 0) }
                        sink.complete()
                    },
                    { exc -> sink.error(exc) }
            )
        }
    }

}