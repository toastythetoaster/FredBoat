package fredboat.event

import com.fredboat.sentinel.entities.VoiceServerUpdate
import fredboat.audio.lavalink.SentinelLavalink
import fredboat.audio.player.PlayerRegistry
import fredboat.config.property.AppConfig
import fredboat.feature.I18n
import fredboat.sentinel.Member
import fredboat.sentinel.VoiceChannel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AudioEventHandler(
        val appConfig: AppConfig,
        val playerRegistry: PlayerRegistry,
        val lavalink: SentinelLavalink
) : SentinelEventHandler() {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(AudioEventHandler::class.java)
    }

    override fun onVoiceJoin(channel: VoiceChannel, member: Member) =
            getLink(channel).setChannel(channel.id.toString())

    override fun onVoiceLeave(channel: VoiceChannel, member: Member) {
        getLink(channel).onDisconnected()
        checkForAutoPause(channel)
    }

    override fun onVoiceMove(oldChannel: VoiceChannel, newChannel: VoiceChannel, member: Member) {
        getLink(newChannel).setChannel(newChannel.id.toString())
        checkForAutoPause(oldChannel)
    }

    override fun onVoiceServerUpdate(voiceServerUpdate: VoiceServerUpdate) =
            lavalink.onVoiceServerUpdate(voiceServerUpdate)

    private fun getLink(channel: VoiceChannel) = lavalink.getLink(channel.guildId.toString())

    private fun checkForAutoPause(channelLeft: VoiceChannel) {
        if (appConfig.continuePlayback)
            return

        val player = playerRegistry.getExisting(channelLeft.guildId) ?: return

        //are we in the channel that someone left from?
        val currentVc = player.currentVoiceChannel
        if (currentVc != null && currentVc.idLong != channelLeft.id) {
            return
        }

        if (player.getHumanUsersInVC(currentVc).isEmpty() && !player.isPaused) {
            player.pause()
            player.activeTextChannel?.sendMessage(I18n.get(channelLeft.guildId).getString("eventUsersLeftVC"))
        }
    }

}