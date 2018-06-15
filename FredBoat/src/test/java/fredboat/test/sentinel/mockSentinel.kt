package fredboat.test.sentinel

import com.fredboat.sentinel.SentinelExchanges
import com.fredboat.sentinel.entities.GuildSubscribeRequest
import com.fredboat.sentinel.entities.SendMessageRequest
import com.fredboat.sentinel.entities.SendMessageResponse
import fredboat.perms.Permission
import fredboat.sentinel.RawGuild
import fredboat.sentinel.RawMember
import fredboat.sentinel.RawTextChannel
import fredboat.test.sentinel.SentinelState.outgoing
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitHandler
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Service
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** State of the fake Rabbit client */
object SentinelState {
    var guild = DefaultSentinelRaws.guild
    val outgoing = mutableMapOf<Class<*>, LinkedBlockingQueue<Any>>()

    fun reset() {
        guild = DefaultSentinelRaws.guild
        outgoing.clear()
    }

    fun <T> poll(type: Class<T>): T? {
        val queue = outgoing.getOrPut(type) { LinkedBlockingQueue() }
        @Suppress("UNCHECKED_CAST")
        return queue.poll(10, TimeUnit.SECONDS) as? T
    }
}

@Service
@Suppress("MemberVisibilityCanBePrivate")
@RabbitListener(queues = [SentinelExchanges.REQUESTS])
class MockSentinelRequestHandler {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(MockSentinelRequestHandler::class.java)
    }

    @RabbitHandler
    fun subscribe(request: GuildSubscribeRequest): RawGuild {
        return SentinelState.guild
    }

    @RabbitHandler
    fun sendMessage(request: SendMessageRequest): SendMessageResponse {
        default(request)
        return SendMessageResponse(-1)
    }

    @RabbitHandler(isDefault = true)
    fun default(request: Any) {
        val queue = outgoing.getOrPut(request.javaClass) { LinkedBlockingQueue() }
        queue.put(request)
    }
}

@Suppress("MemberVisibilityCanBePrivate")
object DefaultSentinelRaws {
    val owner = RawMember(
            81011298891993088,
            "Fre_d",
            "Fred",
            "0310",
            174820236481134592,
            false,
            listOf(),
            null
    )

    val self = RawMember(
            184405311681986560,
            "FredBoat♪♪",
            "FredBoat",
            "7284",
            174820236481134592,
            true,
            listOf(),
            null
    )

    val generalChannel = RawTextChannel(
            174820236481134592,
            "general",
            (Permission.MESSAGE_READ + Permission.MESSAGE_WRITE).raw
    )

    val privateChannel = RawTextChannel(
            184358843206074368,
            "private",
            0
    )

    val guild = RawGuild(
            174820236481134592,
            "FredBoat Hangout",
            owner.id,
            listOf(owner, self),
            listOf(generalChannel, privateChannel),
            listOf(),
            listOf()
    )
}