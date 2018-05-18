package fredboat.event

import com.fredboat.sentinel.entities.VoiceServerUpdate
import fredboat.audio.lavalink.SentinelLavalink
import fredboat.sentinel.Member
import fredboat.sentinel.VoiceChannel
import org.springframework.stereotype.Component

@Component
class AudioEventHandler(val lavalink: SentinelLavalink) : SentinelEventHandler() {

    override fun onVoiceJoin(channel: VoiceChannel, member: Member) {

    }

    override fun onVoiceLeave(channel: VoiceChannel, member: Member) {

    }

    override fun onVoiceMove(oldChannel: VoiceChannel, newChannel: VoiceChannel, member: Member) {

    }

    override fun onVoiceServerUpdate(voiceServerUpdate: VoiceServerUpdate)
            = lavalink.onVoiceServerUpdate(voiceServerUpdate)

}