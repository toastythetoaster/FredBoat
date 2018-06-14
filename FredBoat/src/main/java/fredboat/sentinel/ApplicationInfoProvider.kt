package fredboat.sentinel

import com.fredboat.sentinel.SentinelExchanges
import com.fredboat.sentinel.entities.ApplicationInfo
import com.fredboat.sentinel.entities.ApplicationInfoRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component

@Component
open class ApplicationInfoProvider(private val rabbitTemplate: RabbitTemplate, private val sentinelTracker: SentinelTracker) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ApplicationInfoProvider::class.java)
    }

    @Bean
    open fun applicationInfo(): ApplicationInfo {
        log.info("Retrieving application info")
        return rabbitTemplate.convertSendAndReceive(
                SentinelExchanges.REQUESTS,
                ApplicationInfoRequest()
        ) as ApplicationInfo
    }
}