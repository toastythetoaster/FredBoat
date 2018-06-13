package fredboat.test.sentinel

import com.fredboat.sentinel.entities.MessageReceivedEvent
import fredboat.commandmeta.CommandContextParser
import fredboat.commandmeta.abs.CommandContext
import fredboat.sentinel.RawGuild
import fredboat.sentinel.RawMember
import fredboat.sentinel.RawMessage
import fredboat.sentinel.RawTextChannel
import org.junit.Assert
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

lateinit var contextParser: CommandContextParser

@Service
class CommandContextParserProvider(commandContextParser: CommandContextParser) {
    init {
        contextParser = commandContextParser
    }
}

suspend fun testCommand(context: CommandContext, block: CommandContext.() -> Unit) {
    try {
        context.command(context)
        context.apply(block)
    } catch (e: Exception) {
        throw e
    } finally {
        SentinelState.reset()
    }

}

suspend fun parseContext(
        guild: RawGuild = DefaultSentinelRaws.guild,
        channel: RawTextChannel = DefaultSentinelRaws.generalChannel,
        invoker: RawMember = DefaultSentinelRaws.owner,
        message: String
): CommandContext {
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
    return contextParser.parse(event) ?: throw IllegalArgumentException("Unknown command")
}

fun CommandContext.assertOutgoingContains(testMsg: String = "Assert outgoing message", regex: String) {
    assertOutgoing(testMsg) { it.contains(regex) }
}

fun CommandContext.assertOutgoing(testMsg: String = "Assert outgoing message", assertion: (String) -> Boolean) {
    val message = (SentinelState.outgoingMessages.poll(30, TimeUnit.SECONDS)
            ?: throw TimeoutException("Command failed to send message")) as? RawMessage
            ?: throw IllegalStateException("This method can only handle RawMessage")
    Assert.assertTrue(testMsg, assertion(message.content))
}