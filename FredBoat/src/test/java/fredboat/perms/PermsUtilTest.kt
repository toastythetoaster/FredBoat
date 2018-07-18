package fredboat.perms

import fredboat.db.transfer.GuildPermissions
import fredboat.definitions.PermissionLevel
import fredboat.sentinel.RawMember
import fredboat.sentinel.getGuild
import fredboat.testutil.IntegrationTest
import fredboat.testutil.sentinel.DefaultSentinelRaws
import fredboat.testutil.sentinel.SentinelState
import fredboat.testutil.util.MockGuildPermsService
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test

internal class PermsUtilTest : IntegrationTest() {

    @Test
    fun testBotOwner() {
        assertEquals(PermissionLevel.BOT_OWNER, DefaultSentinelRaws.owner.level)
    }

    @Test
    fun testBotAdmin() {
        SentinelState.setRoles(
                SentinelState.guild,
                DefaultSentinelRaws.napster,
                listOf(DefaultSentinelRaws.botAdminRole.id)
        )

        assertEquals(PermissionLevel.BOT_ADMIN, DefaultSentinelRaws.napster.level)
    }

    @Test
    fun testAdmin(permsService: MockGuildPermsService) {
        permsService.factory = {
            GuildPermissions().apply {
                id = it.id.toString()
                adminList = listOf(DefaultSentinelRaws.adminRole.id.toString())
            }
        }
        assertEquals(PermissionLevel.ADMIN, DefaultSentinelRaws.napster.level)
    }

    @Test
    fun testDj(permsService: MockGuildPermsService) {
        permsService.factory = {
            GuildPermissions().apply {
                id = it.id.toString()
                djList = listOf(DefaultSentinelRaws.adminRole.id.toString())
            }
        }
        assertEquals(PermissionLevel.DJ, DefaultSentinelRaws.napster.level)
    }

    @Test
    fun testUser(permsService: MockGuildPermsService) {
        permsService.factory = {
            GuildPermissions().apply {
                id = it.id.toString()
                userList = listOf(DefaultSentinelRaws.adminRole.id.toString())
            }
        }
        assertEquals(PermissionLevel.USER, DefaultSentinelRaws.napster.level)
    }

    @Test
    fun testBase() {
        assertEquals(PermissionLevel.BASE, DefaultSentinelRaws.napster.level)
    }

    @Test
    fun afterEach(permsService: MockGuildPermsService) {
        permsService.factory = permsService.default
    }

    private val RawMember.level: PermissionLevel
        get() {
            var level: PermissionLevel? = null
            runBlocking {
                val guild = getGuild(DefaultSentinelRaws.guild.id)
                val member = guild!!.getMember(id)
                level = PermsUtil.getPerms(member!!)
            }
            return level!!
        }

}