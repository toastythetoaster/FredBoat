package fredboat.sentinel

import com.fredboat.sentinel.entities.ApplicationInfo
import com.fredboat.sentinel.entities.ApplicationInfoRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.context.annotation.Bean

open class ApplicationInfoProvider(private val rabbitTemplate: RabbitTemplate) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ApplicationInfoProvider::class.java)
    }

    @Bean
    open fun applicationInfo(): ApplicationInfo {
        log.info("Retrieving application info")
        return rabbitTemplate.convertSendAndReceive(ApplicationInfoRequest()) as ApplicationInfo
    }
}