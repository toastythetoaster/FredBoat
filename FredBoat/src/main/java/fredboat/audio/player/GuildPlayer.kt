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

package fredboat.audio.player

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import fredboat.audio.lavalink.SentinelLavalink
import fredboat.audio.queue.*
import fredboat.command.music.control.VoteSkipCommand
import fredboat.commandmeta.MessagingException
import fredboat.commandmeta.abs.CommandContext
import fredboat.db.api.GuildConfigService
import fredboat.definitions.PermissionLevel
import fredboat.definitions.RepeatMode
import fredboat.feature.I18n
import fredboat.perms.Permission
import fredboat.perms.PermsUtil
import fredboat.sentinel.Guild
import fredboat.sentinel.Member
import fredboat.sentinel.TextChannel
import fredboat.sentinel.VoiceChannel
import fredboat.util.extension.escapeAndDefuse
import fredboat.util.ratelimit.Ratelimiter
import fredboat.util.rest.YoutubeAPI
import kotlinx.coroutines.experimental.reactive.awaitSingle
import org.apache.commons.lang3.tuple.ImmutablePair
import org.apache.commons.lang3.tuple.Pair
import org.slf4j.LoggerFactory
import java.util.*
import java.util.function.Consumer
import kotlin.streams.toList

class GuildPlayer(
        val lavalink: SentinelLavalink,
        val guild: Guild,
        private val musicTextChannelProvider: MusicTextChannelProvider,
        audioPlayerManager: AudioPlayerManager,
        private val guildConfigService: GuildConfigService,
        ratelimiter: Ratelimiter,
        youtubeAPI: YoutubeAPI
) : AbstractPlayer(lavalink, SimpleTrackProvider(), guild) {

    private val audioLoader: AudioLoader
    val guildId: Long

    companion object {
        private val log = LoggerFactory.getLogger(GuildPlayer::class.java)

    }

    val trackCount: Int
        get() {
            var trackCount = audioTrackProvider.size()
            if (player.playingTrack != null) trackCount++
            return trackCount
        }

    //Live streams are considered to have a length of 0
    val totalRemainingMusicTimeMillis: Long
        get() {
            var millis = audioTrackProvider.durationMillis

            val currentTrack = if (player.playingTrack != null) context else null
            if (currentTrack != null && !currentTrack.track.info.isStream) {
                millis += Math.max(0, currentTrack.effectiveDuration - position)
            }
            return millis
        }


    val streamsCount: Long
        get() {
            var streams = audioTrackProvider.streamsCount().toLong()
            val atc = if (player.playingTrack != null) context else null
            if (atc != null && atc.track.info.isStream) streams++
            return streams
        }


    val currentVoiceChannel: VoiceChannel?
        get() = guild.selfMember.voiceChannel

    /**
     * @return The text channel currently used for music commands.
     *
     * May return null if the channel was deleted.
     */
    val activeTextChannel: TextChannel?
        get() {
            if (!guild.selfPresent) return null
            return musicTextChannelProvider.getMusicTextChannel(guild)
        }

    /**
     * @return Users who are not bots
     */
    val humanUsersInCurrentVC: List<Member>
        get() = getHumanUsersInVC(currentVoiceChannel)

    var repeatMode: RepeatMode
        get() = if (audioTrackProvider is AbstractTrackProvider)
            audioTrackProvider.repeatMode
        else
            RepeatMode.OFF
        set(repeatMode) = if (audioTrackProvider is AbstractTrackProvider) {
            audioTrackProvider.repeatMode = repeatMode
        } else {
            throw UnsupportedOperationException("Can't repeat " + audioTrackProvider.javaClass)
        }

    var isShuffle: Boolean
        get() = audioTrackProvider is AbstractTrackProvider && audioTrackProvider.isShuffle
        set(shuffle) = if (audioTrackProvider is AbstractTrackProvider) {
            audioTrackProvider.isShuffle = shuffle
        } else {
            throw UnsupportedOperationException("Can't shuffle " + audioTrackProvider.javaClass)
        }

    private val isTrackAnnounceEnabled: Boolean
        get() {
            var enabled = false
            try {
                if (guild.selfPresent) {
                    enabled = guildConfigService.fetchGuildConfig(guild.id).isTrackAnnounce
                }
            } catch (ignored: Exception) {
            }

            return enabled
        }

    init {
        log.debug("Constructing GuildPlayer({})", guild)
        onPlayHook = Consumer { this.announceTrack(it) }
        onErrorHook = Consumer { this.handleError(it) }

        this.guildId = guild.id

        audioLoader = AudioLoader(ratelimiter, audioTrackProvider, audioPlayerManager,
                this, youtubeAPI)
    }

    private fun announceTrack(atc: AudioTrackContext) {
        if (repeatMode != RepeatMode.SINGLE && isTrackAnnounceEnabled && !isPaused) {
            val activeTextChannel = activeTextChannel
            activeTextChannel?.send(atc.i18nFormat("trackAnnounce", atc.effectiveTitle.escapeAndDefuse()))
                    ?.subscribe()
        }
    }

    private fun handleError(t: Throwable) {
        if (t !is MessagingException) {
            log.error("Guild player error", t)
        }
        val activeTextChannel = activeTextChannel
        activeTextChannel?.send("Something went wrong!\n${t.message}")?.subscribe()
    }

    @Throws(MessagingException::class)
    suspend fun joinChannel(usr: Member) {
        val targetChannel = usr.voiceChannel
        joinChannel(targetChannel)
    }

    @Throws(MessagingException::class)
    suspend fun joinChannel(targetChannel: VoiceChannel?) {
        if (targetChannel == null) {
            throw MessagingException(I18n.get(guild).getString("playerUserNotInChannel"))
        }
        if (targetChannel == currentVoiceChannel) {
            // already connected to the channel
            return
        }

        val guild = targetChannel.guild
        val permissions = guild.selfMember.getPermissions(targetChannel).awaitSingle()

        if (permissions hasNot Permission.VOICE_CONNECT && guild.selfMember.voiceChannel != targetChannel) {
            throw MessagingException(I18n.get(guild).getString("playerJoinConnectDenied"))
        }

        if (permissions hasNot Permission.VOICE_SPEAK) {
            throw MessagingException(I18n.get(guild).getString("playerJoinSpeakDenied"))
        }

        if (targetChannel.userLimit > 0
                && targetChannel.userLimit <= targetChannel.members.size
                && permissions hasNot Permission.VOICE_MOVE_OTHERS) {
            throw MessagingException(String.format("The channel you want me to join is full!" +
                    " Please free up some space, or give me the permission to **%s** to bypass the limit.", //todo i18n
                    Permission.VOICE_MOVE_OTHERS.uiName))
        }

        try {
            lavalink.getLink(guild).connect(targetChannel)
            log.info("Connected to voice channel $targetChannel")
        } catch (e: Exception) {
            log.error("Failed to join voice channel {}", targetChannel, e)
        }

    }

    fun leaveVoiceChannelRequest(commandContext: CommandContext, silent: Boolean) {
        if (!silent) {
            val currentVc = commandContext.guild.selfMember.voiceChannel
            if (currentVc == null) {
                commandContext.reply(commandContext.i18n("playerNotInChannel"))
            } else {
                commandContext.reply(commandContext.i18nFormat("playerLeftChannel", currentVc.name))
            }
        }
        lavalink.getLink(guild).disconnect()
    }

    suspend fun queue(identifier: String, context: CommandContext) {
        val ic = IdentifierContext(identifier, context.textChannel, context.member)

        joinChannel(context.member)

        audioLoader.loadAsync(ic)
    }

    suspend fun queue(ic: IdentifierContext) {
        joinChannel(ic.member)

        audioLoader.loadAsync(ic)
    }

    suspend fun queue(atc: AudioTrackContext) {
        if (!guild.selfPresent) return

        val member = guild.getMember(atc.userId)
        if (member != null) {
            joinChannel(member)
        }
        audioTrackProvider.add(atc)
        play()
    }

    //add a bunch of tracks to the track provider
    fun loadAll(tracks: Collection<AudioTrackContext>) {
        audioTrackProvider.addAll(tracks)
    }

    @Suppress("LocalVariableName")
    fun getTracksInRange(start: Int, end: Int): List<AudioTrackContext> {
        // Make mutable
        var start_ = start
        var end_ = end

        val result = ArrayList<AudioTrackContext>()

        //adjust args for whether there is a track playing or not

        if (player.playingTrack != null) {
            if (start_ <= 0) {
                result.add(context!!)
                end_--//shorten the requested range by 1, but still start at 0, since that's the way the trackprovider counts its tracks
            } else {
                //dont add the currently playing track, drop the args by one since the "first" track is currently playing
                start_--
                end_--
            }
        } else {
            //nothing to do here, args are fine to pass on
        }

        result.addAll(audioTrackProvider.getTracksInRange(start_, end_))
        return result
    }

    /** Similar to [getTracksInRange], but only gets the trackIds */
    fun getTrackIdsInRange(start: Int, end: Int): List<Long> = getTracksInRange(start, end).stream()
            .map<Long>({ it.trackId })
            .toList()

    fun getHumanUsersInVC(vc: VoiceChannel?): List<Member> {
        vc ?: return emptyList()
        return vc.members.stream()
                .filter { !it.isBot }
                .toList()
    }

    override fun toString(): String {
        return "[GP:$guildId]"
    }

    fun reshuffle() {
        if (audioTrackProvider is AbstractTrackProvider) {
            audioTrackProvider.reshuffle()
        } else {
            throw UnsupportedOperationException("Can't reshuffle " + audioTrackProvider.javaClass)
        }
    }

    //Success, fail message
    suspend fun canMemberSkipTracks(member: Member, trackIds: Collection<Long>): Pair<Boolean, String> {
        if (PermsUtil.checkPerms(PermissionLevel.DJ, member)) {
            return ImmutablePair(true, null)
        } else {
            //We are not a mod
            val userId = member.id

            //if there is a currently playing track, and the track is requested to be skipped, but not owned by the
            // requesting user, then currentTrackSkippable should be false
            var currentTrackSkippable = true
            val playingTrack = playingTrack
            if (playingTrack != null
                    && trackIds.contains(playingTrack.trackId)
                    && playingTrack.userId != userId) {

                currentTrackSkippable = false
            }

            return if (currentTrackSkippable && audioTrackProvider.isUserTrackOwner(userId, trackIds)) { //check ownership of the queued tracks
                ImmutablePair(true, null)
            } else {
                ImmutablePair(false, I18n.get(guild).getString("skipDeniedTooManyTracks"))
            }
        }
    }

    suspend fun skipTracksForMemberPerms(context: CommandContext, trackIds: Collection<Long>, successMessage: String) {
        val pair = canMemberSkipTracks(context.member, trackIds)

        if (pair.left) {
            context.reply(successMessage)
            skipTracks(trackIds)
        } else {
            context.replyWithName(pair.right)
        }
    }

    fun skipTracks(trackIds: Collection<Long>) {
        var skipCurrentTrack = false

        val toRemove = ArrayList<Long>()
        val playing = if (player.playingTrack != null) context else null
        for (trackId in trackIds) {
            if (playing != null && trackId == playing.trackId) {
                //Should be skipped last, in respect to PlayerEventListener
                skipCurrentTrack = true
            } else {
                toRemove.add(trackId)
            }
        }

        audioTrackProvider.removeAllById(toRemove)

        if (skipCurrentTrack) skip()
    }

    override fun onTrackStart(player: AudioPlayer?, track: AudioTrack?) {
        voteSkipCleanup()
        super.onTrackStart(player, track)
    }

    override fun destroy() {
        audioTrackProvider.clear()
        super.destroy()
        log.info("Player for $guildId was destroyed.")
    }

    private fun voteSkipCleanup() {
        VoteSkipCommand.guildSkipVotes.remove(guildId)
    }
}
