package fredboat.db.mongo

import fredboat.audio.player.GuildPlayer
import fredboat.audio.queue.SplitAudioTrackContext
import lavalink.client.LavalinkUtil
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono

interface PlayerRepository : ReactiveCrudRepository<MongoPlayer, Long> {

    fun save(player: GuildPlayer): Mono<MongoPlayer> {
        val mplayer = player.run { MongoPlayer(
                guildId,
                isPaused,
                isShuffle,
                isPlaying,
                repeatMode.ordinal.toByte(),
                volume,
                playingTrack?.track?.position,
                this.player.link?.getNode(false)?.name,
                remainingTracks.map {
                    if (it is SplitAudioTrackContext) {
                        MongoTrack(it.trackId, LavalinkUtil.toBinary(it.track), it.member.id, it.startPosition, it.endPosition, it.effectiveTitle)
                    } else {
                        MongoTrack(it.trackId, LavalinkUtil.toBinary(it.track), it.member.id, null, null, it.effectiveTitle)
                    }
                }
        )}

        return save(mplayer)
    }

}

