package fredboat.db.mongo

import fredboat.audio.player.GuildPlayer
import fredboat.audio.queue.SplitAudioTrackContext
import lavalink.client.LavalinkUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface PlayerRepository : ReactiveCrudRepository<MongoPlayer, Long>

private val log: Logger = LoggerFactory.getLogger(PlayerRepository::class.java)

fun PlayerRepository.convertAndSave(player: GuildPlayer): Mono<MongoPlayer> {
    return save(player.toMongo())
}

fun PlayerRepository.convertAndSaveAll(players: List<GuildPlayer>): Flux<MongoPlayer> {
    val entities = players.mapNotNull {
        try {
            return@mapNotNull it.toMongo()
        } catch (e: Exception) {
            log.error("Problem saving player state", e)
            return@mapNotNull null
        }
    }
    return saveAll(entities)
}

private fun GuildPlayer.toMongo() = MongoPlayer(
        guildId,
        isPlaying,
        isPaused,
        isShuffle,
        repeatMode.ordinal.toByte(),
        volume,
        playingTrack?.track?.position,
        currentVoiceChannel?.id,
        remainingTracks.map {
            if (it is SplitAudioTrackContext) {
                MongoTrack(it.trackId, LavalinkUtil.toBinary(it.track), it.member.id, it.startPosition, it.endPosition, it.effectiveTitle)
            } else {
                MongoTrack(it.trackId, LavalinkUtil.toBinary(it.track), it.member.id, null, null, it.effectiveTitle)
            }
        }
)
