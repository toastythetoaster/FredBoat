package fredboat.test

import fredboat.main.Launcher
import kotlinx.coroutines.experimental.launch
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import java.lang.Thread.sleep
import java.util.concurrent.TimeoutException

class SharedSpringContext : ParameterResolver {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SharedSpringContext::class.java)
    }

    var application: ApplicationContext

    init {
        log.info("Initializing test context")
        launch { Launcher.main(emptyArray()) }
        var i = 0
        while (Launcher.instance == null) {
            sleep(1000)
            i++
            if (i > 60) throw TimeoutException("Context initialization timed out")
        }
        application = Launcher.instance!!.springContext
        log.info("Successfully initialized test context ${application.displayName}")
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return application.getBean(parameterContext.parameter.type) != null
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
        return application.getBean(parameterContext.parameter.type)
    }

}