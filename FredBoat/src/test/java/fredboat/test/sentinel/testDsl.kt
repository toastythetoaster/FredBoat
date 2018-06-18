package fredboat.test.sentinel

import com.fredboat.sentinel.SentinelExchanges
import com.fredboat.sentinel.entities.MessageReceivedEvent
import com.fredboat.sentinel.entities.SendMessageRequest
import fredboat.commandmeta.CommandContextParser
import fredboat.commandmeta.abs.CommandContext
import fredboat.sentinel.RawGuild
import fredboat.sentinel.RawMember
import fredboat.sentinel.RawTextChannel
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeoutException
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.jvm.reflect

private lateinit var rabbit: RabbitTemplate

@Service
class CommandTester(private val commandContextParser: CommandContextParser, template: RabbitTemplate) {

    init {
        rabbit = template
    }

    fun testCommand(
            message: String,
            guild: RawGuild = DefaultSentinelRaws.guild,
            channel: RawTextChannel = DefaultSentinelRaws.generalChannel,
            invoker: RawMember = DefaultSentinelRaws.owner,
            block: suspend CommandContext.() -> Unit
    ) {
        doTest(parse(
                message, guild, channel, invoker
        ), block)
    }

    private fun doTest(context: CommandContext, block: suspend CommandContext.() -> Unit) {
        runBlocking {
            try {
                context.command(context)
                context.apply { block() }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    private fun parse(
            message: String,
            guild: RawGuild = DefaultSentinelRaws.guild,
            channel: RawTextChannel = DefaultSentinelRaws.generalChannel,
            invoker: RawMember = DefaultSentinelRaws.owner
    ): CommandContext = runBlocking {
        val event = MessageReceivedEvent(
                Math.random().toLong(),
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
    assertReply(testMsg) { it.contains(regex) }
}

fun CommandContext.assertReply(testMsg: String = "Assert outgoing message", assertion: (String) -> Boolean) {
    val message = SentinelState.poll(SendMessageRequest::class.java)
            ?: throw TimeoutException("Command failed to send message")
    Assert.assertTrue(testMsg, assertion(message.message))
}

fun CommandContext.assertReply(expected: String, testMsg: String = "Assert outgoing message") {
    val message = (SentinelState.poll(SendMessageRequest::class.java)
            ?: throw TimeoutException("Command failed to send message"))
    Assert.assertEquals(testMsg, expected, message.message)
}

fun <T> CommandContext.assertRequest(testMsg: String = "Failed to assert outgoing request {}", assertion: (T) -> Boolean) {
    val className = assertion.reflect()!!.parameters[0].type.jvmErasure
    val message = (SentinelState.poll(className.java)
            ?: throw TimeoutException("Command failed to send " + className.simpleName))
    @Suppress("UNCHECKED_CAST")
    Assert.assertTrue(testMsg.replace("{}", message.toString()), assertion(message as T))
}

fun CommandContext.invokerSend(message: String) {
    rabbit.convertSendAndReceive(SentinelExchanges.EVENTS, MessageReceivedEvent(
            Math.random().toLong(), // shrug
            guild.id,
            textChannel.id,
            textChannel.ourEffectivePermissions.raw,
            message,
            member.id,
            false,
            emptyList()
    ))
}