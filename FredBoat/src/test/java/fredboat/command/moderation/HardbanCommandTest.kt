package fredboat.command.moderation

import com.fredboat.sentinel.entities.ModRequest
import com.fredboat.sentinel.entities.ModRequestType
import fredboat.testutil.IntegrationTest
import fredboat.testutil.sentinel.DefaultSentinelRaws
import fredboat.testutil.sentinel.assertRequest
import org.junit.jupiter.api.Test

class HardbanCommandTest : IntegrationTest() {

    @Test
    fun testWorking() {
        testCommand(message = ";;ban realkc Generic shitposting", invoker = DefaultSentinelRaws.napster) {
            assertRequest<ModRequest> {
                it.guildId == guild.id && it.reason.contains("Generic shitposting") && it.type == ModRequestType.BAN
            }
        }
    }

}