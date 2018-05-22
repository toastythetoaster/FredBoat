package fredboat.event

import com.fredboat.sentinel.entities.ShardLifecycleEvent
import com.fredboat.sentinel.entities.ShardStatusChange
import com.fredboat.sentinel.entities.VoiceServerUpdate
import fredboat.sentinel.*
import java.time.Instant

abstract class SentinelEventHandler {

    open fun onShardStatusChange(event: ShardStatusChange) {}
    open fun onShardLifecycle(event: ShardLifecycleEvent) {}

    open fun onGuildJoin(guild: Guild) {}
    open fun onGuildLeave(guild: Guild, joinTime: Instant) {}

    open fun onVoiceJoin(channel: VoiceChannel, member: Member) {}
    open fun onVoiceLeave(channel: VoiceChannel, member: Member) {}
    open fun onVoiceMove(oldChannel: VoiceChannel, newChannel: VoiceChannel, member: Member) {}
    open fun onVoiceServerUpdate(voiceServerUpdate: VoiceServerUpdate) {}

    open fun onGuildMessage(channel: TextChannel, author: Member, content: String) {}
    open fun onGuildMessageDelete(channel: TextChannel, messageId: Long) {}
    open fun onPrivateMessage(author: User, content: String) {}

}
