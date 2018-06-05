package fredboat.sentinel

import com.fredboat.sentinel.SentinelExchanges
import com.fredboat.sentinel.entities.*
import fredboat.config.SentryConfiguration
import fredboat.event.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.amqp.rabbit.annotation.RabbitHandler
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
@RabbitListener(queues = [SentinelExchanges.EVENTS])
class RabbitConsumer(
        private val sentinel: Sentinel,
        private val guildCache: GuildCache,
        eventLogger: EventLogger,
        guildHandler: GuildEventHandler,
        audioHandler: AudioEventHandler,
        messageHandler: MessageEventHandler,
        musicPersistenceHandler: MusicPersistenceHandler,
        shardReviveHandler: ShardReviveHandler
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(RabbitConsumer::class.java)
    }
    private val shardStatuses = ConcurrentHashMap<Int, ShardStatus>()
    private val eventHandlers: List<SentinelEventHandler> = listOf(
            eventLogger,
            guildHandler,
            audioHandler,
            messageHandler,
            musicPersistenceHandler,
            shardReviveHandler
    )

    /* Shard lifecycle */

    @RabbitHandler
    fun receive(event: ShardStatusChange) {
        event.shard.apply {
            log.info("Shard [$id / $total] status ${shardStatuses.getOrDefault(id, "<new>")} => $status")
            shardStatuses[id] = status
        }
        eventHandlers.forEach { it.onShardStatusChange(event) }
    }

    @RabbitHandler
    fun receive(event: ShardLifecycleEvent) {
        eventHandlers.forEach { it.onShardLifecycle(event) }
    }

    /* Guild events */

    @RabbitHandler
    fun receive(event: GuildJoinEvent) {
        log.info("Joined guild ${event.guild}")
        getGuild(event.guild) { guild ->
            eventHandlers.forEach { it.onGuildJoin(guild) }
        }

    }

    @RabbitHandler
    fun receive(event: GuildLeaveEvent) {
        log.info("Left guild ${event.guild}")
        eventHandlers.forEach { it.onGuildLeave(event.guild, event.joinTime) }
    }

    /* Voice events */

    @RabbitHandler
    fun receive(event: VoiceJoinEvent) {
        val guild = guildCache.getIfCached(event.guild) ?: return
        val channel = guild.getVoiceChannel(event.channel)
        val member = guild.getMember(event.member)

        if (channel == null) throw IllegalStateException("Got VoiceJoinEvent for unknown channel ${event.channel}")
        if (member == null) throw IllegalStateException("Got VoiceJoinEvent for unknown member ${event.member}")

        eventHandlers.forEach { it.onVoiceJoin(channel, member) }
    }

    @RabbitHandler
    fun receive(event: VoiceLeaveEvent) {
        val guild = guildCache.getIfCached(event.guild) ?: return
        val channel = guild.getVoiceChannel(event.channel)
        val member = guild.getMember(event.member)

        if (channel == null) throw IllegalStateException("Got VoiceLeaveEvent for unknown channel ${event.channel}")
        if (member == null) throw IllegalStateException("Got VoiceLeaveEvent for unknown member ${event.member}")

        eventHandlers.forEach { it.onVoiceLeave(channel, member) }
    }

    @RabbitHandler
    fun receive(event: VoiceMoveEvent) {
        val guild = guildCache.getIfCached(event.guild) ?: return
        val old = guild.getVoiceChannel(event.oldChannel)
        val new = guild.getVoiceChannel(event.newChannel)
        val member = guild.getMember(event.member)

        if (old == null) throw IllegalStateException("Got VoiceMoveEvent for unknown old channel ${event.oldChannel}")
        if (new == null) throw IllegalStateException("Got VoiceMoveEvent for unknown new channel ${event.newChannel}")
        if (member == null) throw IllegalStateException("Got VoiceMoveEvent for unknown member ${event.member}")

        eventHandlers.forEach { it.onVoiceMove(old, new, member) }
    }

    @RabbitHandler
    fun receive(event: VoiceServerUpdate) {
        eventHandlers.forEach { it.onVoiceServerUpdate(event) }
    }

    /* Message events */

    @RabbitHandler
    fun receive(event: MessageReceivedEvent) {
        // Before execution set some variables that can help with finding traces that belong to each other
        MDC.putCloseable(SentryConfiguration.SENTRY_MDC_TAG_GUILD, event.guild.toString()).use {
            MDC.putCloseable(SentryConfiguration.SENTRY_MDC_TAG_CHANNEL, event.channel.toString()).use {
                MDC.putCloseable(SentryConfiguration.SENTRY_MDC_TAG_INVOKER, event.author.toString()).use {
                    eventHandlers.forEach { it.onGuildMessage(event) }
                }
            }
        }
    }

    @RabbitHandler
    fun receive(event: PrivateMessageReceivedEvent) {
        val author = User(event.author)

        // Before execution set some variables that can help with finding traces that belong to each other
        MDC.putCloseable(SentryConfiguration.SENTRY_MDC_TAG_GUILD, "PRIVATE").use {
            MDC.putCloseable(SentryConfiguration.SENTRY_MDC_TAG_INVOKER, author.id.toString()).use {
                eventHandlers.forEach { it.onPrivateMessage(author, event.content) }
            }
        }
        eventHandlers.forEach { it.onPrivateMessage(author, event.content) }
    }

    @RabbitHandler
    fun receive(event: MessageDeleteEvent) {
        eventHandlers.forEach { it.onGuildMessageDelete(
                event.guild,
                event.channel,
                event.id
        ) }
    }

    @RabbitListener
    fun guildInvalidate(event: GuildInvalidation) = sentinel.guildCache.invalidate(event.id)

    @RabbitHandler(isDefault = true)
    fun default(msg: Any) = log.warn("Unhandled event $msg")
}