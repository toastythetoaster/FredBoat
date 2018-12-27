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

import com.google.common.collect.Lists
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import com.sedmelluq.discord.lavaplayer.track.TrackMarker
import fredboat.audio.lavalink.SentinelLavalink
import fredboat.audio.lavalink.SentinelLink
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
import fredboat.sentinel.*
import fredboat.util.TextUtils
import fredboat.util.extension.escapeAndDefuse
import fredboat.util.ratelimit.Ratelimiter
import fredboat.util.rest.YoutubeAPI
import lavalink.client.player.IPlayer
import lavalink.client.player.LavalinkPlayer
import lavalink.client.player.event.PlayerEventListenerAdapter
import org.apache.commons.lang3.tuple.ImmutablePair
import org.apache.commons.lang3.tuple.Pair
import org.bson.types.ObjectId
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Consumer
import kotlin.streams.toList

class GuildPlayer(
        val lavalink: SentinelLavalink,
        var guild: Guild,
        private val musicTextChannelProvider: MusicTextChannelProvider,
        audioPlayerManager: AudioPlayerManager,
        private val guildConfigService: GuildConfigService,
        ratelimiter: Ratelimiter,
        youtubeAPI: YoutubeAPI
) : PlayerEventListenerAdapter() {

    private val audioTrackProvider: ITrackProvider = SimpleTrackProvider()
    private val audioLoader = AudioLoader(ratelimiter, audioTrackProvider, audioPlayerManager, this, youtubeAPI)
    val guildId = guild.id
    val player: LavalinkPlayer = lavalink.getLink(guild.id.toString()).player
    var internalContext: AudioTrackContext? = null

    private var onPlayHook: Consumer<AudioTrackContext>? = null
    private var onErrorHook: Consumer<Throwable>? = null
    @Volatile
    private var lastLoadedTrack: AudioTrackContext? = null
    private val historyQueue = ConcurrentLinkedQueue<AudioTrackContext>()

    companion object {
        private val log = LoggerFactory.getLogger(GuildPlayer::class.java)
        private const val MAX_HISTORY_SIZE = 20
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

            val currentTrack = if (player.playingTrack != null) internalContext else null
            if (currentTrack != null && !currentTrack.track.info.isStream) {
                millis += Math.max(0, currentTrack.effectiveDuration - position)
            }
            return millis
        }


    val streamsCount: Long
        get() {
            var streams = audioTrackProvider.streamsCount().toLong()
            val atc = if (player.playingTrack != null) internalContext else null
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

    val isQueueEmpty: Boolean
        get() {
            log.trace("isQueueEmpty()")

            return player.playingTrack == null && audioTrackProvider.isEmpty
        }

    val trackCountInHistory: Int
        get() = historyQueue.size

    val isHistoryQueueEmpty: Boolean
        get() = historyQueue.isEmpty()

    val playingTrack: AudioTrackContext?
        get() {
            log.trace("getPlayingTrack()")

            return if (player.playingTrack == null && internalContext == null) {
                audioTrackProvider.peek()
            } else internalContext
        }

    //the unshuffled playlist
    //Includes currently playing track, which comes first
    val remainingTracks: List<AudioTrackContext>
        get() {
            log.trace("getRemainingTracks()")
            val list = ArrayList<AudioTrackContext>()
            val atc = playingTrack
            if (atc != null) {
                list.add(atc)
            }

            list.addAll(audioTrackProvider.asList)
            return list
        }

    var volume: Float
        get() = player.volume.toFloat() / 100
        set(vol) {
            player.volume = (vol * 100).toInt()
        }

    val isPlaying: Boolean
        get() = player.playingTrack != null && !player.isPaused

    val isPaused: Boolean
        get() = player.isPaused

    val position: Long
        get() = player.trackPosition

    init {
        log.debug("Constructing GuildPlayer({})", guild)
        onPlayHook = Consumer { this.announceTrack(it) }
        onErrorHook = Consumer { this.handleError(it) }
        @Suppress("LeakingThis")
        player.addListener(this)
    }

    private fun announceTrack(atc: AudioTrackContext) {
        if (repeatMode != RepeatMode.SINGLE && isTrackAnnounceEnabled && !isPaused) {
            val activeTextChannel = activeTextChannel
            activeTextChannel?.send(atc.i18nFormat(
                    "trackAnnounce",
                    atc.effectiveTitle.escapeAndDefuse(),
                    atc.member.effectiveName.escapeAndDefuse()
            ))?.subscribe()
        }
    }

    private fun handleError(t: Throwable) {
        if (t !is MessagingException) {
            log.error("Guild player error", t)
        }
        val activeTextChannel = activeTextChannel
        activeTextChannel?.send("Something went wrong!\n${t.message}")?.subscribe()
    }

    fun joinChannel(usr: Member) {
        val targetChannel = usr.voiceChannel
        joinChannel(targetChannel)
    }

    fun joinChannel(targetChannel: VoiceChannel?) {
        if (targetChannel == null) {
            throw MessagingException(I18n.get(guild).getString("playerUserNotInChannel"))
        }
        if (targetChannel == currentVoiceChannel) {
            // already connected to the channel
            return
        }

        val guild = targetChannel.guild
        val permissions = targetChannel.ourEffectivePermissions

        if (permissions hasNot Permission.VIEW_CHANNEL) {
            val i18n = I18n.get(guild).getString("permissionMissingBot")
            throw MessagingException("$i18n ${Permission.VIEW_CHANNEL.uiName}")
        }

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

    fun queue(identifier: String, context: CommandContext) {
        val ic = IdentifierContext(identifier, context.textChannel, context.member)

        joinChannel(context.member)

        audioLoader.loadAsync(ic)
    }

    fun queue(ic: IdentifierContext) {
        joinChannel(ic.member)

        audioLoader.loadAsync(ic)
    }

    fun queue(atc: AudioTrackContext) {
        if (!guild.selfPresent) throw IllegalStateException("Attempt to queue track in a guild we are not present in")

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
                result.add(internalContext!!)
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
    fun getTrackIdsInRange(start: Int, end: Int): List<ObjectId> = getTracksInRange(start, end).stream()
            .map { it.trackId }
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
    private suspend fun canMemberSkipTracks(member: Member, trackIds: Collection<ObjectId>): Pair<Boolean, String> {
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

    suspend fun skipTracksForMemberPerms(context: CommandContext, trackIds: Collection<ObjectId>, successMessage: String) {
        val pair = canMemberSkipTracks(context.member, trackIds)

        if (pair.left) {
            context.reply(successMessage)
            skipTracks(trackIds)
        } else {
            context.replyWithName(pair.right)
        }
    }

    fun skipTracks(trackIds: Collection<ObjectId>) {
        var skipCurrentTrack = false

        val toRemove = ArrayList<ObjectId>()
        val playing = if (player.playingTrack != null) internalContext else null
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

    override fun onTrackStart(player: IPlayer?, track: AudioTrack?) {
        voteSkipCleanup()
        super.onTrackStart(player, track)
    }

    fun destroy() {
        audioTrackProvider.clear()
        stop()
        player.removeListener(this)
        player.link.destroy()
        log.info("Player for $guildId was destroyed.")
    }

    private fun voteSkipCleanup() {
        VoteSkipCommand.guildSkipVotes.remove(guildId)
    }

    /**
     * Invoked when subscribing to this player's guild, with an already existing guild
     */
    fun linkPostProcess() {
        val iGuild = guild as InternalGuild
        val vsu = iGuild.cachedVsu
        val slink = player.link as SentinelLink
        if (vsu != null) {
            iGuild.cachedVsu = null
            slink.onVoiceServerUpdate(JSONObject(vsu.raw), vsu.sessionId)
            log.info("Using cached VOICE_SERVER_UPDATE for $guild")

            val vc = guild.selfMember.voiceChannel
            if (vc == null)
                log.warn("Using cached VOICE_SERVER_UPDATE, but it doesn't appear like we are in a voice channel!")
            else
                slink.setChannel(vc.idString)
        } else {
            val vc = slink.getChannel(guild) ?: return
            slink.connect(vc, skipIfSameChannel = false)
        }
    }

    fun play() {
        log.trace("play()")

        if (player.isPaused) {
            player.isPaused = false
        }
        if (player.playingTrack == null) {
            logListeners()
            loadAndPlay()
        }

    }

    fun setPause(pause: Boolean) {
        log.trace("setPause({})", pause)

        if (pause) {
            player.isPaused = true
        } else {
            player.isPaused = false
            play()
        }
    }

    /**
     * Pause the player
     */
    fun pause() {
        log.trace("pause()")

        player.isPaused = true
    }

    /**
     * Clear the tracklist and stop the current track
     */
    fun stop() {
        log.trace("stop()")

        audioTrackProvider.clear()
        stopTrack()
    }

    /**
     * Skip the current track
     */
    fun skip() {
        log.trace("skip()")

        audioTrackProvider.skipped()
        stopTrack()
    }

    /**
     * Stop the current track.
     */
    fun stopTrack() {
        log.trace("stopTrack()")

        internalContext = null
        player.stopTrack()
    }

    fun getTracksInHistory(start: Int, end: Int): List<AudioTrackContext> {
        val start2 = Math.max(start, 0)
        val end2 = Math.max(end, start)
        val historyList = ArrayList(historyQueue)

        return if (historyList.size >= end2) {
            Lists.reverse(ArrayList(historyQueue)).subList(start2, end2)
        } else {
            ArrayList()
        }
    }

    override fun onTrackEnd(player: IPlayer?, track: AudioTrack?, endReason: AudioTrackEndReason?) {
        log.debug("onTrackEnd({} {} {}) called", track!!.info.title, endReason!!.name, endReason.mayStartNext)

        if (endReason == AudioTrackEndReason.FINISHED || endReason == AudioTrackEndReason.STOPPED) {
            updateHistoryQueue()
            loadAndPlay()
        } else if (endReason == AudioTrackEndReason.CLEANUP) {
            log.info("Track " + track.identifier + " was cleaned up")
        } else if (endReason == AudioTrackEndReason.LOAD_FAILED) {
            if (onErrorHook != null)
                onErrorHook!!.accept(MessagingException("Track `" + TextUtils.escapeAndDefuse(track.info.title) + "` failed to load. Skipping..."))
            audioTrackProvider.skipped()
            loadAndPlay()
        } else {
            log.warn("Track " + track.identifier + " ended with unexpected reason: " + endReason)
        }
    }

    //request the next track from the track provider and start playing it
    private fun loadAndPlay() {
        log.trace("loadAndPlay()")

        val atc = audioTrackProvider.provideAudioTrack()
        lastLoadedTrack = atc
        atc?.let { playTrack(it) }
    }

    private fun updateHistoryQueue() {
        if (lastLoadedTrack == null) {
            log.warn("No lastLoadedTrack in $this after track end")
            return
        }
        if (historyQueue.size == MAX_HISTORY_SIZE) {
            historyQueue.poll()
        }
        historyQueue.add(lastLoadedTrack)
    }

    /**
     * Plays the provided track.
     *
     *
     * Silently playing a track will not trigger the onPlayHook (which announces the track usually)
     */
    private fun playTrack(trackContext: AudioTrackContext, silent: Boolean = false) {
        log.trace("playTrack({})", trackContext.effectiveTitle)

        internalContext = trackContext
        player.playTrack(trackContext.track)
        trackContext.track.position = trackContext.startPosition

        if (trackContext is SplitAudioTrackContext) {
            //Ensure we don't step over our bounds
            log.info("Start: ${trackContext.startPosition} End: ${trackContext.startPosition + trackContext.effectiveDuration}")

            trackContext.track.setMarker(
                    TrackMarker(trackContext.startPosition + trackContext.effectiveDuration,
                            TrackEndMarkerHandler(this, trackContext)))
        }

        if (!silent && onPlayHook != null) onPlayHook!!.accept(trackContext)
    }

    override fun onTrackException(player: IPlayer?, track: AudioTrack, exception: Exception?) {
        log.error("Lavaplayer encountered an exception while playing {}",
                track.identifier, exception)
    }

    override fun onTrackStuck(player: IPlayer?, track: AudioTrack, thresholdMs: Long) {
        log.error("Lavaplayer got stuck while playing {}",
                track.identifier)
    }

    fun seekTo(position: Long) {
        if (internalContext!!.track.isSeekable) {
            player.seekTo(position)
        } else {
            throw MessagingException(internalContext!!.i18n("seekDeniedLiveTrack"))
        }
    }

    private fun logListeners() {
        humanUsersInCurrentVC.forEach { lavalink.activityMetrics.logListener(it) }
    }
}
