package fredboat.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fredboat.sentinel.SentinelExchanges
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.AsyncRabbitTemplate
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.retry.interceptor.RetryInterceptorBuilder

@Configuration
class RabbitConfiguration {

    @Bean
    fun jsonMessageConverter(): MessageConverter {
        // We must register this Kotlin module to get deserialization to work with data classes
        return Jackson2JsonMessageConverter(ObjectMapper().registerKotlinModule())
    }

    @Bean
    fun asyncTemplate(rabbitTemplate: RabbitTemplate): AsyncRabbitTemplate {
        return AsyncRabbitTemplate(rabbitTemplate)
    }

    @Bean
    fun eventQueue() = Queue(SentinelExchanges.EVENTS, false)

    /* Don't retry ad infinitum */
    @Bean
    fun retryOperationsInterceptor() = RetryInterceptorBuilder
            .stateful()
            .maxAttempts(3)
            .build()!!
}