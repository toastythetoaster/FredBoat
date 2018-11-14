package fredboat.db.mongo

import fredboat.audio.player.GuildPlayer
import lavalink.client.LavalinkUtil
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono

interface PlayerRepository : ReactiveCrudRepository<MongoPlayer, String> {

    fun save(player: GuildPlayer): Mono<MongoPlayer> {
        val mplayer = player.run { MongoPlayer(
                guildId,
                isPaused,
                isShuffle,
                repeatMode.ordinal.toByte(),
                volume,
                playingTrack?.track?.position,
                this.player.link?.getNode(false)?.name,
                remainingTracks.map {
                    MongoTrack(it.trackId, LavalinkUtil.toBinary(it.track), it.member.id)
                }
        )}
        return save(mplayer)
    }

}

