package fredboat.event

import fredboat.audio.player.PlayerRegistry
import fredboat.command.info.HelloCommand
import fredboat.db.api.GuildSettingsRepository
import fredboat.feature.metrics.Metrics
import fredboat.sentinel.Guild
import fredboat.sentinel.TextChannel
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant

@Component
class GuildEventHandler(
        private val repo: GuildSettingsRepository,
        private val playerRegistry: PlayerRegistry
) : SentinelEventHandler() {
    override fun onGuildJoin(guild: Guild) {
        // Wait a few seconds to allow permissions to be set and applied and propagated
        Mono.create<Unit> { sendHelloOnJoin(guild) }
                .delaySubscription(Duration.ofSeconds(10))
                .subscribe()
    }

    override fun onGuildLeave(guildId: Long, joinTime: Instant) {
        playerRegistry.destroyPlayer(guildId)

        val lifespan = Instant.now().epochSecond - joinTime.epochSecond
        Metrics.guildLifespan.observe(lifespan.toDouble())
    }

    private fun sendHelloOnJoin(guild: Guild) {
        repo.fetch(guild.id).subscribe { gs ->

            //filter guilds that already received a hello message
            // useful for when discord trolls us with fake guild joins
            // or to prevent it send repeatedly due to kick and reinvite
            if (gs.helloSent) {
                return@subscribe
            }

            var channel: TextChannel? = guild.getTextChannel(guild.id) //old public channel
            if (channel == null || !channel.canTalk()) {
                //find first channel that we can talk in
                guild.textChannels.forEach { _, tc ->
                    if (tc.canTalk()) {
                        channel = tc
                        return@forEach
                    }
                }
            }
            gs.helloSent = true
            channel?.send(HelloCommand.getHello(guild))?.doOnSuccess { repo.update(gs) }?.subscribe()
        }
    }
}