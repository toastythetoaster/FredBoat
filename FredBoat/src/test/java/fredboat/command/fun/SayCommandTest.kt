package fredboat.command.`fun`

import fredboat.config.QuarterdeckConfiguration
import fredboat.test.sentinel.CommandTester
import fredboat.test.sentinel.assertOutgoing
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@EnableAutoConfiguration(exclude = [ // Excluded because we manage these already
    DataSourceAutoConfiguration::class,
    DataSourceTransactionManagerAutoConfiguration::class,
    HibernateJpaAutoConfiguration::class,
    FlywayAutoConfiguration::class,
    QuarterdeckConfiguration::class])
@SpringBootTest(classes = [CommandTester::class])
@ComponentScan(basePackages = ["fredboat"])
@SpringBootApplication()
open class SayCommandTest {

    @Autowired
    lateinit var commandTester: CommandTester

    @Test
    fun onInvoke() {
        commandTester.parseAndTest(";;say qwe rty") {
            assertOutgoing("qwe rty")
        }
    }
}