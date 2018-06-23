package fredboat.command.music.control

import com.fredboat.sentinel.entities.AudioQueueRequest
import com.fredboat.sentinel.entities.AudioQueueRequestEnum
import com.fredboat.sentinel.entities.EditMessageRequest
import fredboat.audio.player.PlayerRegistry
import fredboat.audio.player.VideoSelectionCache
import fredboat.test.IntegrationTest
import fredboat.test.sentinel.DefaultSentinelRaws
import fredboat.test.sentinel.SentinelState
import fredboat.test.sentinel.assertReply
import fredboat.test.sentinel.assertRequest
import kotlinx.coroutines.experimental.delay
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class PlayCommandTest : IntegrationTest() {

    @Test
    fun notInChannel() {
        testCommand(";;play demetori") {
            assertReply("You must join a voice channel first.")
        }
    }

    @Test
    fun search(selections: VideoSelectionCache, players: PlayerRegistry) {
        SentinelState.joinChannel()
        var editedMessage = -1L
        var selection: VideoSelectionCache.VideoSelection? = null
        testCommand(";;play demetori") {
            assertReply("Searching YouTube for `demetori`...")
            assertRequest { req: EditMessageRequest ->
                editedMessage = req.messageId
                req.message.startsWith("**Please select a track with the `;;play 1-5` command:**")
            }
            selection = selections[member]
            assertNotNull(selection)
            assertEquals(selection!!.message.id, editedMessage)
        }
        testCommand(";;play 5") {
            assertRequest { req: EditMessageRequest ->
                assertEquals(req.messageId, editedMessage)
                req.message.contains("Song **#5** has been selected")
            }
            Assert.assertNull(selections[member])
            assertNotNull(players.getExisting(guild))
            assertEquals(selection!!.choices[4], players.getExisting(guild)!!.playingTrack?.track)
            assertEquals(member, players.getExisting(guild)!!.playingTrack?.member)
        }
    }

    @Test
    fun playUrl(players: PlayerRegistry) {
        SentinelState.joinChannel(channel = DefaultSentinelRaws.musicChannel)
        val url = "https://www.youtube.com/watch?v=8EdW28B-In4"
        testCommand(";;play $url") {
            assertRequest<AudioQueueRequest> { it.channel == DefaultSentinelRaws.musicChannel.id }
            assertReply { it.contains("Best of Demetori") && it.contains("will now play") }
            assertNotNull(players.getExisting(guild))
            var i = 0
            while (players.getOrCreate(guild).playingTrack == null) {
                delay(200)
                i++
                if (i < 10) break
            }
            assertNotNull(players.getOrCreate(guild).playingTrack)
            assertEquals(url, players.getOrCreate(guild).playingTrack?.track?.info?.uri)
        }
    }

    @BeforeEach
    fun beforeEach(players: PlayerRegistry) {
        players.destroyPlayer(SentinelState.guild.id)
        SentinelState.reset()
        val req = SentinelState.poll(AudioQueueRequest::class.java, timeoutMillis = 2000)
        req?.let { assertEquals(
                "Expected disconnect (if anything at all)",
                AudioQueueRequestEnum.QUEUE_DISCONNECT,
                it.type
        ) }
    }
}