package fredboat.testutil.extensions

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class RetryRule(val maxAttempts: Int = 3) : TestRule {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(RetryRule::class.java)
    }

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                for (i in 1..maxAttempts) {
                    try {
                        base.evaluate()
                        return
                    } catch (t: Throwable) {
                        log.error("${description.displayName}: Run $i of $maxAttempts failed", t)
                    }

                }
                log.error(description.displayName + ": Giving up after " + maxAttempts + " failures")
            }
        }
    }


}