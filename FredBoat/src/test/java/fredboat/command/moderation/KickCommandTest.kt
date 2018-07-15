package fredboat.command.moderation

import com.fredboat.sentinel.entities.ModRequestType
import fredboat.testutil.sentinel.DefaultSentinelRaws
import fredboat.testutil.sentinel.SentinelState
import fredboat.testutil.sentinel.assertReply
import fredboat.testutil.sentinel.assertReplyContains
import org.junit.jupiter.api.Test

class KickCommandTest : AbstractModerationTest() {

    @Test
    fun testWorking() {
        testCommand(message = ";;kick realkc Generic shitposting", invoker = DefaultSentinelRaws.napster) {
            expect(ModRequestType.KICK, DefaultSentinelRaws.realkc, "Generic shitposting")
        }
    }

    @Test
    fun testWorkingOwner() {
        testCommand(message = ";;kick napster Generic shitposting", invoker = DefaultSentinelRaws.owner) {
            expect(ModRequestType.KICK, DefaultSentinelRaws.napster, "Generic shitposting")
        }
    }

    @Test
    fun testUnprivileged() {
        testCommand(message = ";;kick napster Generic shitposting", invoker = DefaultSentinelRaws.realkc) {
            assertReply("You need the following permission to perform that action: **Kick Members**")
        }
    }

    @Test
    fun testBotUnprivileged() {
        SentinelState.setRoles(member = DefaultSentinelRaws.self, roles = emptyList())
        testCommand(message = ";;kick realkc Generic shitposting", invoker = DefaultSentinelRaws.napster) {
            assertReplyContains("I need the following permission to perform that action: **Kick Members**")
        }
    }

    @Test
    fun testOwnerTargetFail() {
        testCommand(message = ";;kick fre_d", invoker = DefaultSentinelRaws.napster) {
            assertReplyContains(i18n("kickFailOwner"))
        }
    }

    @Test
    fun testSelfFail() {
        testCommand(message = ";;kick napster", invoker = DefaultSentinelRaws.napster) {
            assertReplyContains(i18n("kickFailSelf"))
        }
    }

    @Test
    fun testMyselfFail() {
        testCommand(message = ";;kick fredboat", invoker = DefaultSentinelRaws.napster) {
            assertReplyContains(i18n("kickFailMyself"))
        }
    }

    /* Hierachy fails */

    @Test
    fun testSameRoleFail() {
        SentinelState.setRoles(member = DefaultSentinelRaws.realkc, roles = listOf(DefaultSentinelRaws.adminRole.id))
        testCommand(message = ";;kick realkc", invoker = DefaultSentinelRaws.napster) {
            assertReplyContains(i18nFormat("modFailUserHierarchy", DefaultSentinelRaws.realkc.nickname!!))
        }
    }

    @Test
    fun testBotSameRoleFail() {
        SentinelState.setRoles(member = DefaultSentinelRaws.self, roles = listOf(DefaultSentinelRaws.adminRole.id))
        SentinelState.setRoles(member = DefaultSentinelRaws.realkc, roles = listOf(DefaultSentinelRaws.adminRole.id))
        SentinelState.setRoles(member = DefaultSentinelRaws.napster, roles = listOf(DefaultSentinelRaws.uberAdminRole.id))
        testCommand(message = ";;kick realkc", invoker = DefaultSentinelRaws.napster) {
            assertReplyContains(i18nFormat("modFailBotHierarchy", DefaultSentinelRaws.realkc.nickname!!))
        }
    }

}