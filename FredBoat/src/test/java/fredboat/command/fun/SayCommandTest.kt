package fredboat.command.`fun`

import fredboat.testutil.IntegrationTest
import fredboat.testutil.sentinel.assertReply
import fredboat.util.TextUtils
import org.junit.jupiter.api.Test

class SayCommandTest : IntegrationTest() {
    @Test
    fun onInvoke() {
        testCommand(";;say qwe rty") {
            assertReply(TextUtils.ZERO_WIDTH_CHAR + "qwe rty")
        }
    }
}