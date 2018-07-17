package fredboat.command.moderation

import com.fredboat.sentinel.entities.ModRequestType
import fredboat.testutil.sentinel.DefaultSentinelRaws
import fredboat.testutil.sentinel.SentinelState
import fredboat.testutil.sentinel.assertReply
import fredboat.testutil.sentinel.assertReplyContains
import org.junit.jupiter.api.Test

class UnbanCommandTest : AbstractModerationTest() {

    private val target = DefaultSentinelRaws.banList[0].user

    @Test
    fun testWorking() {
        testCommand(";;unban ${target.name}", invoker = DefaultSentinelRaws.napster) {
            expect(ModRequestType.UNBAN, target.id)
        }
    }

    @Test
    fun testWorkingOwner() {
        testCommand(";;unban ${target.id}", invoker = DefaultSentinelRaws.owner) {
            expect(ModRequestType.UNBAN, target.id)
        }
    }

    @Test
    fun testUnprivileged() {
        testCommand(message = ";;unban ${target.id}", invoker = DefaultSentinelRaws.realkc) {
            assertReply("You need the following permission to perform that action: **Ban Members**")
        }
    }

    @Test
    fun testBotUnprivileged() {
        SentinelState.setRoles(member = DefaultSentinelRaws.self, roles = emptyList())
        testCommand(message = ";;unban ${target.id}", invoker = DefaultSentinelRaws.napster) {
            assertReplyContains("I need the following permission to perform that action: **Ban Members**")
        }
    }

}