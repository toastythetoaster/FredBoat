package fredboat.command.music.control

import fredboat.test.IntegrationTest
import fredboat.test.sentinel.assertReply
import fredboat.test.util.cachedGuild
import fredboat.test.util.queue
import org.junit.jupiter.api.Test

internal class DestroyCommandTest : IntegrationTest() {

    @Test
    fun onInvoke() {
        cachedGuild.queue(PlayCommandTest.url)
        testCommand(";;Destroy") {
            assertReply("${member.effectiveName}: Reset the player and cleared the queue.")
        }
    }
}