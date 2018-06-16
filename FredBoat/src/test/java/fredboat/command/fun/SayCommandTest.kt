package fredboat.command.`fun`

import fredboat.test.extensions.SharedSpringContext
import fredboat.test.sentinel.CommandTester
import fredboat.test.sentinel.assertReply
import fredboat.util.TextUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(/*DockerExtension::class, */SharedSpringContext::class)
class SayCommandTest {
    @Test
    fun onInvoke(commandTester: CommandTester) {
        commandTester.parseAndTest(";;say qwe rty") {
            assertReply(TextUtils.ZERO_WIDTH_CHAR + "qwe rty")
        }
    }
}