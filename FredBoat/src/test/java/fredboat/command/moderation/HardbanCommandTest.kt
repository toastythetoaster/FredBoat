package fredboat.command.moderation

import com.fredboat.sentinel.entities.ModRequestType
import fredboat.testutil.sentinel.DefaultSentinelRaws
import org.junit.jupiter.api.Test

class HardbanCommandTest : AbstractModerationTest() {

    @Test
    fun testWorking() {
        testCommand(message = ";;ban realkc Generic shitposting", invoker = DefaultSentinelRaws.napster) {
            expect(ModRequestType.BAN, DefaultSentinelRaws.realkc, "Generic shitposting")
        }
    }

    @Test
    fun testWorkingOwner() {
        testCommand(message = ";;ban napster Generic shitposting", invoker = DefaultSentinelRaws.owner) {
            expect(ModRequestType.BAN, DefaultSentinelRaws.napster, "Generic shitposting")
        }
    }

}