/*
 * MIT License
 *
 * Copyright (c) 2017 Frederik Ar. Mikkelsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package fredboat.audio.queue

import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import fredboat.audio.player.GuildPlayer
import fredboat.main.Launcher
import fredboat.messaging.internal.NullableContext
import fredboat.sentinel.Guild
import fredboat.sentinel.Member
import fredboat.sentinel.TextChannel
import fredboat.sentinel.User
import org.bson.types.ObjectId
import java.util.concurrent.ThreadLocalRandom

open class AudioTrackContext(
        val track: AudioTrack,
        override val member: Member,
        priority: Boolean = false
) : NullableContext(), Comparable<AudioTrackContext> {

    val trackId: ObjectId // used to identify this track even when the track gets cloned and the rand reranded
    var rand: Int = if (!priority) 0 else Integer.MIN_VALUE
    var isPriority: Boolean = priority
    val added: Long = System.currentTimeMillis()

    override val guild: Guild
        get() = member.guild

    override val user: User
        get() = member.user

    val userId: Long
        get() = member.id

    val guildId: Long
        get() = member.guild.id

    open val effectiveDuration: Long
        get() = track.duration

    open val effectiveTitle: String
        get() = track.info.title

    open val startPosition: Long
        get() = 0

    //return the currently active text channel of the associated guildplayer
    override val textChannel: TextChannel?
        get() {
            val guildPlayer = Launcher.botController.playerRegistry.getExisting(guildId)
            return guildPlayer?.activeTextChannel
        }

    val thumbnailUrl: String? get() {
        return if (track is YoutubeAudioTrack) {
            "https://img.youtube.com/vi/${track.info.identifier}/mqdefault.jpg"
        } else null
    }

    init {
        this.rand = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE)
        this.trackId = ObjectId()
    }//It's ok to set a non-existing channelId, since inside the AudioTrackContext, the channel needs to be looked up
    // every time. See the getTextChannel() below for doing that.

    fun randomize(): Int {
        rand = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE)
        return rand
    }

    open fun makeClone(): AudioTrackContext {
        return AudioTrackContext(track.makeClone(), member, isPriority)
    }

    //NOTE: convenience method that returns the position of the track currently playing in the guild where this track was added
    open fun getEffectivePosition(guildPlayer: GuildPlayer): Long {
        return guildPlayer.position
    }

    override fun compareTo(other: AudioTrackContext): Int {
        return Integer.compare(rand, other.rand)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioTrackContext) return false

        if (track != other.track) return false
        if (member != other.member) return false
        if (trackId != other.trackId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = track.hashCode()
        result = 31 * result + member.hashCode()
        result = 31 * result + trackId.hashCode()
        return result
    }
}
