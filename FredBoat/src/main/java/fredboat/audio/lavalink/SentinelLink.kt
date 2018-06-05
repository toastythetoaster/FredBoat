package fredboat.audio.lavalink

import com.fredboat.sentinel.entities.AudioQueueRequest
import com.fredboat.sentinel.entities.AudioQueueRequestEnum.*
import fredboat.perms.InsufficientPermissionException
import fredboat.perms.Permission.*
import fredboat.sentinel.VoiceChannel
import lavalink.client.io.Link

class SentinelLink(val lavalink: SentinelLavalink, guildId: String) : Link(lavalink, guildId) {
    private val routingKey: String
            get() {
                val sId = ((guildId.toLong() shr 22) % lavalink.appConfig.shardCount.toLong()).toInt()
                return lavalink.sentinel.tracker.getKey(sId)
            }

    override fun removeConnection() =
            lavalink.sentinel.sendAndForget(routingKey, AudioQueueRequest(REMOVE, guildId.toLong()))

    override fun queueAudioConnect(channelId: Long) =
            lavalink.sentinel.sendAndForget(routingKey, AudioQueueRequest(QUEUE_CONNECT, guildId.toLong(), channelId))

    override fun queueAudioDisconnect() =
            lavalink.sentinel.sendAndForget(routingKey, AudioQueueRequest(QUEUE_DISCONNECT, guildId.toLong()))

    fun connect(channel: VoiceChannel) {
        if (channel.guild.id != guild)
            throw IllegalArgumentException("The provided VoiceChannel is not a part of the Guild that this AudioManager " +
                    "handles. Please provide a VoiceChannel from the proper Guild")

        val perms = channel.ourEffectivePermissions

        if (perms hasNot VOICE_CONNECT && perms hasNot VOICE_MOVE_OTHERS)
            throw InsufficientPermissionException(VOICE_CONNECT, "We do not have permission to join $channel")
        perms.assertHas(VOICE_SPEAK, "We do not have permission to speak in $channel")

        // Do nothing if we are already connected
        if (super.getChannel() == channel.id.toString()) return

        if (channel.userLimit > 1 // Is there a user limit?
                && channel.userLimit <= channel.members.size // Is that limit reached?
        ){
            perms.assertHas(VOICE_MOVE_OTHERS, "$channel already has [${channel.members.size}/${channel.userLimit}] " +
                    "members, and we don't have $VOICE_MOVE_OTHERS to bypass the limit.")
        }

        state = Link.State.CONNECTING
        queueAudioConnect(channel.id)
    }

}