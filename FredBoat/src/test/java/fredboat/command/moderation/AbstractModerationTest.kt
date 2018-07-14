package fredboat.command.moderation

import com.fredboat.sentinel.entities.ModRequest
import com.fredboat.sentinel.entities.ModRequestType
import fredboat.commandmeta.abs.CommandContext
import fredboat.sentinel.RawMember
import fredboat.testutil.IntegrationTest
import fredboat.testutil.sentinel.assertRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

open class AbstractModerationTest : IntegrationTest() {
    protected fun CommandContext.expect(type: ModRequestType, target: RawMember, reasonContains: String? = null) {
        assertRequest<ModRequest> {
            assertEquals(guild.id, it.guildId)
            assertEquals(target.id, it.userId)
            reasonContains?.run {
                assertTrue("Expected reason to contain $reasonContains", it.reason.contains(reasonContains))
            }
            assertEquals(type, it.type)
            true
        }
    }
}