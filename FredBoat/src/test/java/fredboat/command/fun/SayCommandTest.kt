package fredboat.command.`fun`

import fredboat.test.SharedSpringContext
import fredboat.test.sentinel.CommandTester
import fredboat.test.sentinel.assertOutgoing
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(SharedSpringContext::class)
open class SayCommandTest {

    @Test
    fun onInvoke(commandTester: CommandTester) {
        commandTester.parseAndTest(";;say qwe rty") {
            assertOutgoing("qwe rty")
        }
    }
}