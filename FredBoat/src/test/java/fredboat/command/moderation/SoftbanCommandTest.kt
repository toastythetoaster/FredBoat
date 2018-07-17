package fredboat.command.moderation

import com.fredboat.sentinel.entities.ModRequestType
import fredboat.testutil.sentinel.DefaultSentinelRaws
import org.junit.jupiter.api.Test

class SoftbanCommandTest : AbstractModerationTest() {

    @Test
    fun testWorking() {
        testCommand(";;softban realkc", invoker = DefaultSentinelRaws.napster) {
            expect(ModRequestType.BAN, DefaultSentinelRaws.realkc)
            expect(ModRequestType.UNBAN, DefaultSentinelRaws.realkc)
        }
    }

    @Test
    fun testWorkingOwner() {
        testCommand(";;softban realkc", invoker = DefaultSentinelRaws.owner) {
            expect(ModRequestType.BAN, DefaultSentinelRaws.realkc)
            expect(ModRequestType.UNBAN, DefaultSentinelRaws.realkc)
        }
    }

    /*
    working
    workingOwner
    missingkick
    missingban
     */

}