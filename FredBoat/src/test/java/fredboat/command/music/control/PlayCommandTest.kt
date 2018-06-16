package fredboat.command.music.control

import com.fredboat.sentinel.entities.EditMessageRequest
import fredboat.audio.player.PlayerRegistry
import fredboat.audio.player.VideoSelectionCache
import fredboat.test.IntegrationTest
import fredboat.test.sentinel.SentinelState
import fredboat.test.sentinel.assertReply
import fredboat.test.sentinel.assertRequest
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.jupiter.api.Test
import java.lang.Thread.sleep

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
        sleep(500)
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
                req.message.startsWith("Song **#5** has been selected")
            }
            Assert.assertNull(selections[member])
            assertNotNull(players.getExisting(guild))
            assertEquals(players.getExisting(guild)!!.playingTrack?.track, selection!!.choices[4])
            assertEquals(players.getExisting(guild)!!.playingTrack?.member, member)
        }
    }
}