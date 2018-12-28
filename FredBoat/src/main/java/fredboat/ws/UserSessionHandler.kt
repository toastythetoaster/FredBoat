package fredboat.ws

import com.google.gson.Gson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

@Controller
class UserSessionHandler(val gson: Gson) : TextWebSocketHandler() {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(UserSessionHandler::class.java)
        val WebSocketSession.info: SocketInfo get() = attributes["info"] as SocketInfo
        fun WebSocketSession.send(gson: Gson, payload: Any) {
            sendMessage(TextMessage(gson.toJson(payload)))
        }
    }

    private val sessions = ConcurrentHashMap<Long, MutableList<WebSocketSession>>()

    operator fun get(guildId: Long): List<WebSocketSession> = sessions[guildId] ?: emptyList()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        log.info("Established user connection for guild ${session.info.guildId}")
        sessions.computeIfAbsent(session.info.guildId) { mutableListOf() }.add(session)
        val info = session.info.player?.toPlayerInfo() ?: emptyPlayerInfo
        session.send(gson, info)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val id = session.info.guildId
        log.info("Disconnected user for guild $id with status $status")
        val list = sessions[id] ?: return
        if (list.size == 1) sessions.remove(id)
        else list.remove(session)
    }

    final inline fun sendLazy(guildId: Long, producer: () -> Any) {
        val sessions = this[guildId]
        if (sessions.isEmpty()) return

        val msg = TextMessage(gson.toJson(producer()))
        sessions.forEach {
            if (it.isOpen) it.sendMessage(msg)
        }
    }

}