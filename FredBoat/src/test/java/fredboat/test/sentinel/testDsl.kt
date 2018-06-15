package fredboat.test.sentinel

import com.fredboat.sentinel.entities.MessageReceivedEvent
import fredboat.commandmeta.CommandContextParser
import fredboat.commandmeta.abs.CommandContext
import fredboat.sentinel.RawGuild
import fredboat.sentinel.RawMember
import fredboat.sentinel.RawTextChannel
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Service
class CommandTester(private val commandContextParser: CommandContextParser) {

    fun parseAndTest(
            message: String,
            guild: RawGuild = DefaultSentinelRaws.guild,
            channel: RawTextChannel = DefaultSentinelRaws.generalChannel,
            invoker: RawMember = DefaultSentinelRaws.owner,
            block: CommandContext.() -> Unit
    ) {
        testCommand(parseContext(
                message, guild, channel, invoker
        ), block)
    }

    fun testCommand(context: CommandContext, block: CommandContext.() -> Unit) { runBlocking {
        try {
            context.command(context)
            context.apply(block)
        } catch (e: Exception) {
            throw e
        } finally {
            SentinelState.reset()
        }
    }
    }

    fun parseContext(
            message: String,
            guild: RawGuild = DefaultSentinelRaws.guild,
            channel: RawTextChannel = DefaultSentinelRaws.generalChannel,
            invoker: RawMember = DefaultSentinelRaws.owner
    ): CommandContext = runBlocking {
        val event = MessageReceivedEvent(
                -1,
                guild.id,
                channel.id,
                channel.ourEffectivePermissions,
                message,
                invoker.id,
                false,
                emptyList()
        )
        return@runBlocking commandContextParser.parse(event) ?: throw IllegalArgumentException("Unknown command")
    }
}

fun CommandContext.assertOutgoingContains(testMsg: String = "Assert outgoing message", regex: String) {
    assertOutgoing(testMsg) { it.contains(regex) }
}

fun CommandContext.assertOutgoing(testMsg: String = "Assert outgoing message", assertion: (String) -> Boolean) {
    val message = (SentinelState.outgoingMessages.poll(30, TimeUnit.SECONDS)
            ?: throw TimeoutException("Command failed to send message"))
    Assert.assertTrue(testMsg, assertion(message))
}

fun CommandContext.assertOutgoing(expected: String, testMsg: String = "Assert outgoing message") {
    val message = (SentinelState.outgoingMessages.poll(30, TimeUnit.SECONDS)
            ?: throw TimeoutException("Command failed to send message"))
    Assert.assertEquals(testMsg, expected, message)
}