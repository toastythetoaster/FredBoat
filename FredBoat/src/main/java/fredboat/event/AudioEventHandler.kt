package fredboat.event

import com.fredboat.sentinel.entities.VoiceServerUpdate
import fredboat.audio.lavalink.SentinelLavalink
import fredboat.audio.player.PlayerRegistry
import fredboat.config.property.AppConfig
import fredboat.db.api.GuildConfigService
import fredboat.feature.I18n
import fredboat.sentinel.Member
import fredboat.sentinel.VoiceChannel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AudioEventHandler(
        private val appConfig: AppConfig,
        private val playerRegistry: PlayerRegistry,
        private val lavalink: SentinelLavalink,
        private val guildConfigService: GuildConfigService
) : SentinelEventHandler() {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(AudioEventHandler::class.java)
    }

    override fun onVoiceJoin(channel: VoiceChannel, member: Member) {
        checkForAutoResume(channel, member)
        if (!member.isUs) return
        getLink(channel).setChannel(channel.id.toString())
    }

    override fun onVoiceLeave(channel: VoiceChannel, member: Member) {
        checkForAutoPause(channel)
        if (!member.isUs) return
        getLink(channel).onDisconnected()
    }

    override fun onVoiceMove(oldChannel: VoiceChannel, newChannel: VoiceChannel, member: Member) {
        checkForAutoResume(newChannel, member)
        if (!member.isUs) return
        getLink(newChannel).setChannel(newChannel.id.toString())
        checkForAutoPause(oldChannel)
    }

    override fun onVoiceServerUpdate(voiceServerUpdate: VoiceServerUpdate) =
            lavalink.onVoiceServerUpdate(voiceServerUpdate)

    private fun getLink(channel: VoiceChannel) = lavalink.getLink(channel.guild.idString)

    private fun checkForAutoPause(channelLeft: VoiceChannel) {
        if (appConfig.continuePlayback) return

        val player = playerRegistry.getExisting(channelLeft.guild.id) ?: return

        //are we in the channel that someone left from?
        val currentVc = player.currentVoiceChannel
        if (currentVc != null && currentVc.id != channelLeft.id) {
            return
        }

        if (player.getHumanUsersInVC(currentVc).isEmpty() && !player.isPaused) {
            player.pause()
            player.activeTextChannel?.send(I18n.get(channelLeft.guild).getString("eventUsersLeftVC"))?.subscribe()
        }
    }

    private fun checkForAutoResume(joinedChannel: VoiceChannel, joined: Member) {
        val guild = joinedChannel.guild
        val player = playerRegistry.getExisting(guild) ?: return

        //ignore bot users that aren't us joining / moving
        if (joined.isBot && !joined.isUs)
            return

        if (player.isPaused
                && player.playingTrack != null
                && joinedChannel.members.contains(guild.selfMember)
                && player.humanUsersInCurrentVC.isNotEmpty()
                && guildConfigService.fetchGuildConfig(guild).isAutoResume) {
            player.setPause(false)
            player.activeTextChannel?.send(I18n.get(guild).getString("eventAutoResumed"))?.subscribe()
        }
    }

}