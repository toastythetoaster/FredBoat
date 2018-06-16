package fredboat.test.extensions

import com.palantir.docker.compose.connection.Container
import com.palantir.docker.compose.connection.State
import com.palantir.docker.compose.connection.waiting.SuccessOrFailure
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import fredboat.test.extensions.DockerExtension.docker
import fredboat.test.extensions.DockerExtension.execute
import fredboat.util.rest.Http
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object DockerHealthChecks {

    private val log: Logger = LoggerFactory.getLogger(DockerHealthChecks::class.java)

    //executing it via the built in DockerComposeRule#exec() is not possible due to quotation marks handling
    fun checkPostgres(c: Container)= wrap(c) { container ->
        val id = docker.dockerCompose().id(container)
        val dockerCommand = "src/test/resources/is-db-init.sh " + id.get()
        val result = execute(dockerCommand)

        return if (result.equals("1", ignoreCase = true)) {
            SuccessOrFailure.success()
        } else {
            SuccessOrFailure.failure("not ready yet")
        }
    }

    fun checkQuarterdeck(c: Container) = wrap(c) { container ->
        if (container.state() == State.DOWN) container.up()

        val port = container.ports().stream().findAny().get()
        val versions = Http(Http.DEFAULT_BUILDER)["http://${port.ip}:${port.externalPort}/api/versions"].asString()
        log.info("Quarterdeck versions supported: $versions")
        SuccessOrFailure.success()
    }

    fun checkRabbitMq(c: Container) = wrap(c) { container ->
        val port = container.ports().stream().findAny().get()

        val factory = ConnectionFactory()
        factory.host = port.ip
        factory.port = port.externalPort
        var conn: Connection? = null
        try {
            conn = factory.newConnection()
        } finally {
            conn?.close()
        }
        SuccessOrFailure.success()
    }

    private inline fun wrap(container: Container, func: (Container) -> SuccessOrFailure): SuccessOrFailure {
        log.info("Running health check for ${container.containerName}")
        try {
            val id = docker.dockerCompose().id(container)
            if (!id.isPresent) {
                return SuccessOrFailure.failure("no id on container")
            }
            return func(container)
        } catch (e: Exception) {
            return SuccessOrFailure.fromException(e)
        }
    }

}