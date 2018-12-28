package fredboat.ws

import fredboat.audio.player.GuildPlayer
import fredboat.audio.player.getTracksInRange
import fredboat.audio.queue.AudioTrackContext
import fredboat.definitions.RepeatMode

data class PlayerInfo(
        val playing: Boolean,
        val paused: Boolean,
        val shuffled: Boolean,
        val repeatMode: Int,
        val playingPos: Long?,
        val queue: List<TrackInfo>
)

data class TrackInfo(
        val id: String,
        val name: String,
        val image: String?,
        val duration: Long?
)

val emptyPlayerInfo = PlayerInfo(false, false, false, RepeatMode.OFF.ordinal, null, emptyList())

fun GuildPlayer.toPlayerInfo() = PlayerInfo(
        isPlaying,
        isPaused,
        isShuffle,
        repeatMode.ordinal,
        playingTrack?.getEffectivePosition(this),
        getTracksInRange(0, 10).map { it.toTrackInfo() }
)

fun AudioTrackContext.toTrackInfo() = TrackInfo(
        trackId.toHexString(),
        effectiveTitle,
        null, //TODO
        if (track.info.isStream) null else effectiveDuration
)