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
               private val blockingTemplate: RabbitTemplate,
               val tracker: SentinelTracker) {

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
        blockingTemplate.convertAndSend(SentinelExchanges.FANOUT, FredBoatHello())
    }

    fun sendAndForget(routingKey: String, request: Any) {
        blockingTemplate.convertAndSend(SentinelExchanges.REQUESTS, routingKey, request)
    }

    fun <T> send(guild: Guild, request: Any): Mono<T> = send(guild.routingKey, request)

    fun <T> send(routingKey: String, request: Any): Mono<T> = Mono.create<T> {
        template.convertSendAndReceive<T>(routingKey, request)
                .addCallback(
                        { res ->
                            if (res != null) it.success(res) else it.success()
                        },
                        { exc -> it.error(exc) }
                )
    }

    internal fun <R, T> genericMonoSendAndReceive(
            exchange: String = SentinelExchanges.REQUESTS,
            routingKey: String,
            request: Any,
            mayBeEmpty: Boolean = false,
            transform: (response: R) -> T) = Mono.create<T> {
        template.convertSendAndReceive<R?>(exchange, routingKey, request).addCallback(
                { res ->
                    if (res == null) {
                        if (mayBeEmpty) it.success()
                        else it.error(RuntimeException("RPC response was null"))
                    } else it.success(transform(res))
                },
                { t ->
                    it.error(t)
                }
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

    fun sendMessage(routingKey: String, channel: TextChannel, message: IMessage): Mono<SendMessageResponse> =
            genericMonoSendAndReceive<SendMessageResponse, SendMessageResponse>(
                    SentinelExchanges.REQUESTS,
                    routingKey,
                    SendMessageRequest(channel.id, message),
                    mayBeEmpty = false,
                    transform = {it}
            )

    fun sendPrivateMessage(user: User, message: IMessage): Mono<Unit> =
            genericMonoSendAndReceive<Unit, Unit>(
                    SentinelExchanges.REQUESTS,
                    tracker.getKey(0),
                    SendPrivateMessageRequest(user.id, message),
                    mayBeEmpty = true,
                    transform = {}
            )

    fun editMessage(channel: TextChannel, messageId: Long, message: IMessage): Mono<Unit> =
            genericMonoSendAndReceive<Unit, Unit>(
                    SentinelExchanges.REQUESTS,
                    channel.guild.routingKey,
                    EditMessageRequest(channel.id, messageId, message),
                    mayBeEmpty = true,
                    transform = {}
            )

    fun deleteMessages(channel: TextChannel, messages: List<Long>): Mono<Unit> =
            genericMonoSendAndReceive<Unit, Unit>(
                    SentinelExchanges.REQUESTS,
                    channel.guild.routingKey,
                    MessageDeleteRequest(channel.id, messages),
                    mayBeEmpty = true,
                    transform = {}
            )

    fun sendTyping(channel: TextChannel) {
        val req = SendTypingRequest(channel.id)
        blockingTemplate.convertAndSend(SentinelExchanges.REQUESTS, channel.guild.routingKey, req)
    }

    private var cachedApplicationInfo: ApplicationInfo? = null
    fun getApplicationInfo(): ApplicationInfo {
        if (cachedApplicationInfo != null) return cachedApplicationInfo as ApplicationInfo

        cachedApplicationInfo = blockingTemplate.convertSendAndReceive(ApplicationInfoRequest()) as ApplicationInfo
        return cachedApplicationInfo!!
    }

    /* Permissions */

    private fun checkPermissions(member: Member?, role: Role?, permissions: IPermissionSet): Mono<PermissionCheckResponse> {
        val guild = member?.guild ?: role!!.guild

        return genericMonoSendAndReceive<PermissionCheckResponse, PermissionCheckResponse>(
                SentinelExchanges.REQUESTS,
                guild.routingKey,
                GuildPermissionRequest(guild.id, member?.id, role?.id, permissions.raw),
                mayBeEmpty = true,
                transform = {it}
        )
    }

    // Role and member are mutually exclusive
    fun checkPermissions(member: Member, permissions: IPermissionSet) = checkPermissions(member, null, permissions)

    fun checkPermissions(role: Role, permissions: IPermissionSet) = checkPermissions(null, role, permissions)

    fun checkPermissions(channel: Channel, member: Member?, role: Role?, permissions: IPermissionSet): Mono<PermissionCheckResponse> {
        val guild = member?.guild ?: role!!.guild

        return genericMonoSendAndReceive<PermissionCheckResponse, PermissionCheckResponse>(
                SentinelExchanges.REQUESTS,
                guild.routingKey,
                ChannelPermissionRequest(channel.id, member?.id, role?.id, permissions.raw),
                mayBeEmpty = true,
                transform = {it}
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
            if (it.guild.id != guild.id) throw IllegalArgumentException("All members must be of the same guild")
            it.id
        })

        return Flux.create { sink ->
            template.convertSendAndReceive<BulkGuildPermissionResponse>(SentinelExchanges.REQUESTS, guild.routingKey, req).addCallback(
                    { r ->
                        r!!.effectivePermissions.forEach { sink.next(it ?: 0) }
                        sink.complete()
                    },
                    { exc -> sink.error(exc) }
            )
        }
    }

    /* Extended info requests */

    fun getGuildInfo(guild: Guild): Mono<GuildInfo> =
            genericMonoSendAndReceive<GuildInfo, GuildInfo>(
                    SentinelExchanges.REQUESTS,
                    guild.routingKey,
                    GuildInfoRequest(guild.id),
                    mayBeEmpty = false,
                    transform = {it}
            )

    fun getMemberInfo(member: Member): Mono<MemberInfo> =
            genericMonoSendAndReceive<MemberInfo, MemberInfo>(
                    SentinelExchanges.REQUESTS,
                    member.guild.routingKey,
                    MemberInfoRequest(member.id, member.guild.id),
                    mayBeEmpty = false,
                    transform = {it}
            )

    fun getRoleInfo(role: Role): Mono<RoleInfo> =
            genericMonoSendAndReceive<RoleInfo, RoleInfo>(
                    SentinelExchanges.REQUESTS,
                    role.guild.routingKey,
                    RoleInfoRequest(role.id),
                    mayBeEmpty = false,
                    transform = {it}
            )

    /* Mass requests */

    data class NamedSentinelInfoResponse(
            val response: SentinelInfoResponse,
            val routingKey: String
    )

    private fun getSentinelInfo(routingKey: String, includeShards: Boolean = false) =
            genericMonoSendAndReceive<SentinelInfoResponse, NamedSentinelInfoResponse>(
            SentinelExchanges.REQUESTS,
            routingKey,
            SentinelInfoRequest(includeShards),
            mayBeEmpty = true,
            transform = {NamedSentinelInfoResponse(it, routingKey)}
    )

    /** Request sentinel info from each tracked Sentinel simultaneously in the order of receiving them
     *  Errors are delayed till all requests have either completed or failed */
    fun getAllSentinelInfo(includeShards: Boolean = false): Flux<NamedSentinelInfoResponse> {
        return Flux.mergeSequentialDelayError(
                tracker.sentinels.map { getSentinelInfo(it.key, includeShards) },
                2,
                0 // Not sure what this is -- hardly even documented
        )
    }

    private fun getSentinelUserList(routingKey: String): Flux<Long> = Flux.create { sink ->
        template.convertSendAndReceive<List<Long>>(SentinelExchanges.REQUESTS, routingKey, UserListRequest()).addCallback(
                {list ->
                    if (list == null) sink.error(NullPointerException())
                    list!!.forEach { sink.next(it) }
                    sink.complete()
                },
                { e -> sink.error(e) }
        )
    }

    /** Request user IDs from each tracked Sentinel simultaneously in the order of receiving them
     *  Errors are delayed till all requests have either completed or failed */
    fun getFullSentinelUserList(): Flux<Long> {
        return Flux.mergeSequentialDelayError(
                tracker.sentinels.map { getSentinelUserList(it.key) },
                1,
                0 // Not sure what this is -- hardly even documented
        )
    }

}