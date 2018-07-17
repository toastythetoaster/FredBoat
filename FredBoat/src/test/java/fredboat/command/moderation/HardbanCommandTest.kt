package fredboat.command.moderation

import com.fredboat.sentinel.entities.ModRequestType
import fredboat.testutil.sentinel.DefaultSentinelRaws
import fredboat.testutil.sentinel.SentinelState
import fredboat.testutil.sentinel.assertReply
import fredboat.testutil.sentinel.assertReplyContains
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

    @Test
    fun testUnprivileged() {
        testCommand(message = ";;ban napster Generic shitposting", invoker = DefaultSentinelRaws.realkc) {
            assertReply("You need the following permission to perform that action: **Ban Members**")
        }
    }

    @Test
    fun testBotUnprivileged() {
        SentinelState.setRoles(member = DefaultSentinelRaws.self, roles = emptyList())
        testCommand(message = ";;ban realkc Generic shitposting", invoker = DefaultSentinelRaws.napster) {
            assertReplyContains("I need the following permission to perform that action: **Ban Members**")
        }
    }

    @Test
    fun testOwnerTargetFail() {
        testCommand(message = ";;ban fre_d", invoker = DefaultSentinelRaws.napster) {
            assertReplyContains(i18n("hardbanFailOwner"))
        }
    }

    @Test
    fun testSelfFail() {
        testCommand(message = ";;ban napster", invoker = DefaultSentinelRaws.napster) {
            assertReplyContains(i18n("hardbanFailSelf"))
        }
    }

    @Test
    fun testMyselfFail() {
        testCommand(message = ";;ban fredboat", invoker = DefaultSentinelRaws.napster) {
            assertReplyContains(i18n("hardbanFailMyself"))
        }
    }

    /* Hierachy fails */

    @Test
    fun testSameRoleFail() {
        SentinelState.setRoles(member = DefaultSentinelRaws.realkc, roles = listOf(DefaultSentinelRaws.adminRole.id))
        testCommand(message = ";;ban realkc", invoker = DefaultSentinelRaws.napster) {
            assertReplyContains(i18nFormat("modFailUserHierarchy", DefaultSentinelRaws.realkc.nickname!!))
        }
    }

    @Test
    fun testBotSameRoleFail() {
        SentinelState.setRoles(member = DefaultSentinelRaws.self, roles = listOf(DefaultSentinelRaws.adminRole.id))
        SentinelState.setRoles(member = DefaultSentinelRaws.realkc, roles = listOf(DefaultSentinelRaws.adminRole.id))
        SentinelState.setRoles(member = DefaultSentinelRaws.napster, roles = listOf(DefaultSentinelRaws.uberAdminRole.id))
        testCommand(message = ";;ban realkc", invoker = DefaultSentinelRaws.napster) {
            assertReplyContains(i18nFormat("modFailBotHierarchy", DefaultSentinelRaws.realkc.nickname!!))
        }
    }

}