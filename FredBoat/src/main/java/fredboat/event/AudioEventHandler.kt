package fredboat.event

import com.fredboat.sentinel.entities.VoiceServerUpdate
import fredboat.audio.lavalink.SentinelLavalink
import fredboat.sentinel.Member
import fredboat.sentinel.VoiceChannel
import org.springframework.stereotype.Component

@Component
class AudioEventHandler(val lavalink: SentinelLavalink) : SentinelEventHandler() {

    override fun onVoiceJoin(channel: VoiceChannel, member: Member) =
            getLink(channel).setChannel(channel.id.toString())

    override fun onVoiceLeave(channel: VoiceChannel, member: Member) =
            getLink(channel).onDisconnected()

    override fun onVoiceMove(oldChannel: VoiceChannel, newChannel: VoiceChannel, member: Member) =
            getLink(newChannel).setChannel(newChannel.id.toString())

    override fun onVoiceServerUpdate(voiceServerUpdate: VoiceServerUpdate) =
            lavalink.onVoiceServerUpdate(voiceServerUpdate)

    private fun getLink(channel: VoiceChannel) = lavalink.getLink(channel.guildId.toString())

}