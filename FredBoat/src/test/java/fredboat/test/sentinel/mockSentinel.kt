package fredboat.test.sentinel

import com.fredboat.sentinel.SentinelExchanges
import com.fredboat.sentinel.entities.*
import fredboat.perms.Permission
import fredboat.sentinel.*
import fredboat.test.sentinel.SentinelState.outgoing
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitHandler
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private lateinit var rabbit: RabbitTemplate
lateinit var guildCache: GuildCache

/** State of the fake Rabbit client */
object SentinelState {
    var guild = DefaultSentinelRaws.guild
    val outgoing = mutableMapOf<Class<*>, LinkedBlockingQueue<Any>>()
    private val log: Logger = LoggerFactory.getLogger(SentinelState::class.java)

    fun reset() {
        Thread.sleep(200) // Give messages a bit of time to come in - prevents race conditions
        log.info("Resetting sentinel state")

        guild = DefaultSentinelRaws.guild.copy()
        outgoing.clear()
        guildCache.cache.remove(guild.id)
        //rabbit.convertAndSend(SentinelExchanges.EVENTS, GuildUpdateEvent(DefaultSentinelRaws.guild))
    }

    fun <T> poll(type: Class<T>, timeoutMillis: Long = 5000): T? {
        val queue = outgoing.getOrPut(type) { LinkedBlockingQueue() }
        @Suppress("UNCHECKED_CAST")
        return queue.poll(timeoutMillis, TimeUnit.MILLISECONDS) as? T
    }

    fun joinChannel(
            member: RawMember = DefaultSentinelRaws.owner,
            channel: RawVoiceChannel = DefaultSentinelRaws.musicChannel
    ) {
        val newList = guild.voiceChannels.toMutableList().apply {
            var removed: RawVoiceChannel? = null
            removeIf { removed = it; it.id == channel.id }
            val membersSet = removed?.members?.toMutableSet() ?: mutableListOf<Long>()
            membersSet.add(member.id)
            add(channel.copy(members = membersSet.toList()))
        }
        guild = guild.copy(voiceChannels = newList)
        guild = setMember(guild, member.copy(voiceChannel = channel.id))
        guildCache.get(guild.id).block(Duration.ofSeconds(4)) // Our event gets ignored if this is not cached and we time out
        rabbit.convertAndSend(SentinelExchanges.EVENTS, VoiceJoinEvent(
                DefaultSentinelRaws.guild.id,
                channel.id,
                member.id))

        log.info("Emulating ${member.name} joining ${channel.name}")
        delayUntil(timeout = 4000) { guildCache.getIfCached(guild.id)?.getMember(member.id)?.voiceChannel?.id == channel.id }
        if (guildCache.getIfCached(guild.id)?.getMember(member.id)?.voiceChannel?.id != channel.id) {
            val info = mapOf(
                    "guild" to guildCache.getIfCached(guild.id),
                    "member" to guildCache.getIfCached(guild.id)?.getMember(member.id),
                    "vc" to guildCache.getIfCached(guild.id)?.getMember(member.id)?.voiceChannel
            )
            throw RuntimeException("Failed to join VC. Debug info: $info")
        }
        log.info("${member.name} joined ${channel.name}")
    }

    private fun setMember(guild: RawGuild, member: RawMember): RawGuild {
        return guild.copy(members = guild.members.toMutableSet().apply { add(member) }.toList())
    }
}

@Service
@Suppress("MemberVisibilityCanBePrivate")
@RabbitListener(queues = [SentinelExchanges.REQUESTS])
class MockSentinelRequestHandler(template: RabbitTemplate, cache: GuildCache) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(MockSentinelRequestHandler::class.java)
    }

    init {
        rabbit = template
        guildCache = cache
    }

    @RabbitHandler
    fun subscribe(request: GuildSubscribeRequest): RawGuild {
        default(request)
        log.info("Got subscription request")
        return SentinelState.guild
    }

    @RabbitHandler
    fun sendMessage(request: SendMessageRequest): SendMessageResponse {
        default(request)
        log.info("FredBoat says: ${request.message}")
        return SendMessageResponse(Math.random().toLong())
    }

    @RabbitHandler
    fun editMessage(request: EditMessageRequest): SendMessageResponse {
        default(request)
        log.info("FredBoat edited: ${request.message}")
        return SendMessageResponse(request.messageId)
    }

    @RabbitHandler(isDefault = true)
    fun default(request: Any) {
        val queue = outgoing.getOrPut(request.javaClass) { LinkedBlockingQueue() }
        queue.put(request)
    }
}

/** Don't use immutable lists here. We want to be able to modify state directly */
@Suppress("MemberVisibilityCanBePrivate")
object DefaultSentinelRaws {
    val owner = RawMember(
            81011298891993088,
            "Fre_d",
            "Fred",
            "0310",
            174820236481134592,
            false,
            mutableListOf(),
            null
    )

    val self = RawMember(
            152691313123393536,
            "FredBoat♪♪",
            "FredBoat",
            "7284",
            174820236481134592,
            true,
            mutableListOf(),
            null
    )

    val generalChannel = RawTextChannel(
            174820236481134592,
            "general",
            (Permission.MESSAGE_READ + Permission.MESSAGE_WRITE).raw
    )

    val privateChannel = RawTextChannel(
            184358843206074368,
            "private",
            0
    )

    val musicChannel = RawVoiceChannel(
            226661001754443776,
            "Music",
            mutableListOf(),
            5,
            (Permission.VOICE_CONNECT + Permission.VOICE_SPEAK).raw
    )

    val guild = RawGuild(
            174820236481134592,
            "FredBoat Hangout",
            owner.id,
            mutableListOf(owner, self),
            mutableListOf(generalChannel, privateChannel),
            mutableListOf(musicChannel),
            mutableListOf()
    )
}