package fredboat.ws

import fredboat.db.mongo.GuildSettingsRepository
import fredboat.sentinel.GuildCache
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Controller
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import java.time.Duration
import java.util.regex.Pattern

@Controller
class HandshakeInterceptorImpl(
        private val guildCache: GuildCache,
        private val guildSettingsRepository: GuildSettingsRepository
) : HandshakeInterceptor {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(HandshakeInterceptorImpl::class.java)
        private val expctedPath = Pattern.compile("/playerinfo/(\\d+)/?")
    }

    override fun beforeHandshake(
            request: ServerHttpRequest,
            response: ServerHttpResponse,
            wsHandler: WebSocketHandler,
            attributes: MutableMap<String, Any>
    ): Boolean {
        expctedPath.matcher(request.uri.path).apply {
            if (!find()) {
                response.setStatusCode(HttpStatus.NOT_FOUND)
                log.info("Unexpected path: {}", request.uri.path)
                return false
            }
            val guildId = group(1).toLong()
            val settings = guildSettingsRepository.findById(guildId).block(Duration.ofMinutes(1))
            if (settings?.allowPublicPlayerInfo != true) {
                throw RuntimeException("This guild does not allow anonymous access to player info.")
            }
            val info = SocketInfo(guildCache, guildId)
            attributes["info"] = info
            return true
        }
    }

    override fun afterHandshake(
            request: ServerHttpRequest,
            response: ServerHttpResponse,
            wsHandler: WebSocketHandler,
            exception: Exception?
    ) {
        if (exception == null) {
            log.error("Exception in handshake", exception)
        }
    }
}