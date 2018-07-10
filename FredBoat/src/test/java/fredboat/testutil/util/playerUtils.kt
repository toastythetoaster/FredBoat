package fredboat.testutil.util

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import fredboat.audio.player.GuildPlayer
import fredboat.audio.queue.AudioTrackContext
import fredboat.main.Launcher
import fredboat.sentinel.Guild
import fredboat.sentinel.InternalVoiceChannel
import fredboat.sentinel.Member
import fredboat.testutil.sentinel.DefaultSentinelRaws
import fredboat.testutil.sentinel.SentinelState
import fredboat.testutil.sentinel.guildCache
import java.time.Duration
import java.util.concurrent.TimeUnit

private val loadedTracks = mutableMapOf<String, AudioTrack>()

fun Guild.queue(
        identifier: String,
        member: Member = getMember(DefaultSentinelRaws.owner.id)!!
): GuildPlayer {
    val player = Launcher.botController.playerRegistry.getOrCreate(this)
    val track = loadedTracks.getOrPut(identifier) {
        val loader = TestAudioLoader()
        Launcher.botController.playerRegistry.audioPlayerManager.loadItem(identifier, loader)
                .get(10, TimeUnit.SECONDS)
        loader.loaded
    }

    if (member.voiceChannel == null) {
        (getVoiceChannel(DefaultSentinelRaws.musicChannel.id)!! as InternalVoiceChannel).handleVoiceJoin(member)
        SentinelState.joinChannel(DefaultSentinelRaws.owner)
    }

    player.queue(AudioTrackContext(track, member))
    return player
}

private class TestAudioLoader : AudioLoadResultHandler {
    lateinit var loaded: AudioTrack

    override fun loadFailed(exception: FriendlyException) = throw exception
    override fun trackLoaded(track: AudioTrack) {loaded = track}
    override fun noMatches() = throw IllegalStateException("No matches")
    override fun playlistLoaded(playlist: AudioPlaylist) {TODO()}
}

val cachedGuild get() = guildCache.get(DefaultSentinelRaws.guild.id).block(Duration.ofSeconds(5))!!