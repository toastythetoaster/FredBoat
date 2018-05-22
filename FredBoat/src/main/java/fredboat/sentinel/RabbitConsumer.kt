package fredboat.sentinel

import com.fredboat.sentinel.QueueNames
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
@RabbitListener(queues = [QueueNames.JDA_EVENTS_QUEUE])
class RabbitConsumer(
        private val sentinel: Sentinel,
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
        log.info("Joined guild ${event.guildId}")
        eventHandlers.forEach { it.onGuildJoin(Guild(event.guildId)) }
    }

    @RabbitHandler
    fun receive(event: GuildLeaveEvent) {
        log.info("Left guild ${event.guildId}")
        eventHandlers.forEach { it.onGuildLeave(Guild(event.guildId), event.joinTime) }
    }

    /* Voice events */

    @RabbitHandler
    fun receive(event: VoiceJoinEvent) {
        val channel = VoiceChannel(event.channel, event.guildId)
        val member = Member(event.member)
        eventHandlers.forEach { it.onVoiceJoin(channel, member) }
    }

    @RabbitHandler
    fun receive(event: VoiceLeaveEvent) {
        val channel = VoiceChannel(event.channel, event.guildId)
        val member = Member(event.member)
        eventHandlers.forEach { it.onVoiceLeave(channel, member) }
    }

    @RabbitHandler
    fun receive(event: VoiceMoveEvent) {
        val old = VoiceChannel(event.oldChannel, event.guildId)
        val new = VoiceChannel(event.newChannel, event.guildId)
        val member = Member(event.member)
        eventHandlers.forEach { it.onVoiceMove(old, new, member) }
    }

    @RabbitHandler
    fun receive(event: VoiceServerUpdate) {
        eventHandlers.forEach { it.onVoiceServerUpdate(event) }
    }

    /* Message events */

    @RabbitHandler
    fun receive(event: MessageReceivedEvent) {
        val channel = TextChannel(event.channel, event.guildId)
        val author = Member(event.author)

        // Before execution set some variables that can help with finding traces that belong to each other
        MDC.putCloseable(SentryConfiguration.SENTRY_MDC_TAG_GUILD, channel.guild.id.toString()).use {
            MDC.putCloseable(SentryConfiguration.SENTRY_MDC_TAG_CHANNEL, channel.id.toString()).use {
                MDC.putCloseable(SentryConfiguration.SENTRY_MDC_TAG_INVOKER, author.id.toString()).use {
                    eventHandlers.forEach { it.onGuildMessage(channel, author, event.content) }
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
                TextChannel(event.channel, event.guildId),
                event.id
        ) }
    }

    @RabbitListener
    fun guildInvalidate(event: GuildInvalidation) = sentinel.guildCache.invalidate(event.id)

    @RabbitHandler(isDefault = true)
    fun default(msg: Any) = log.warn("Unhandled event $msg")
}