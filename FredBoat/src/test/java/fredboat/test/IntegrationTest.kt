package fredboat.test

import fredboat.commandmeta.abs.CommandContext
import fredboat.sentinel.RawGuild
import fredboat.sentinel.RawMember
import fredboat.sentinel.RawTextChannel
import fredboat.test.extensions.DockerExtension
import fredboat.test.extensions.SharedSpringContext
import fredboat.test.sentinel.CommandTester
import fredboat.test.sentinel.DefaultSentinelRaws
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(DockerExtension::class, SharedSpringContext::class)
open class IntegrationTest : BaseTest() {
    companion object {
        lateinit var commandTester: CommandTester // Set by SharedSpringContext
    }

    fun testCommand(
            message: String,
            guild: RawGuild = DefaultSentinelRaws.guild,
            channel: RawTextChannel = DefaultSentinelRaws.generalChannel,
            invoker: RawMember = DefaultSentinelRaws.owner,
            block: CommandContext.() -> Unit
    ) {
        commandTester.testCommand(message, guild, channel, invoker, block)
    }
}