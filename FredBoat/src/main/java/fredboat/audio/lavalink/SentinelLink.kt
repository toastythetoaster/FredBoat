package fredboat.audio.lavalink

import com.fredboat.sentinel.entities.AudioQueueRequest
import com.fredboat.sentinel.entities.AudioQueueRequestEnum.*
import fredboat.sentinel.VoiceChannel
import lavalink.client.io.Link

class SentinelLink(val lavalink: SentinelLavalink, guildId: String) : Link(lavalink, guildId) {
    override fun removeConnection() =
            lavalink.sentinel.sendAndForget(AudioQueueRequest(REMOVE, guildId.toLong()))

    override fun queueAudioConnect(channelId: Long) =
            lavalink.sentinel.sendAndForget(AudioQueueRequest(QUEUE_CONNECT, guildId.toLong(), channelId))

    override fun queueAudioDisconnect() =
            lavalink.sentinel.sendAndForget(AudioQueueRequest(QUEUE_DISCONNECT, guildId.toLong()))

    fun connect(channel: VoiceChannel) {
        zz
    }

}