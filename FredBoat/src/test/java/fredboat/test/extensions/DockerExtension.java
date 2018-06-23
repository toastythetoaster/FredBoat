package fredboat.test.extensions;

/*
 * MIT License
 *
 * Copyright (c) 2016-2018 The FredBoat Org https://github.com/FredBoat/
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.configuration.ProjectName;
import com.palantir.docker.compose.configuration.ShutdownStrategy;
import com.palantir.docker.compose.connection.waiting.HealthChecks;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * Created by napster on 30.03.18.
 *
 * This extension will create necessary dependencies before the first test that is using this extension is run,
 * and will clean up the docker container during shutdown. The database will be reused between all tests using this
 * extension, with setup run only once.
 * Do not kill the test execution via SIGKILL (this happens when running with IntelliJ's debug mode and clicking the
 * stop button), or else you might end up with orphaned docker containers on your machine.
 */
public class DockerExtension implements BeforeAllCallback {

    private static final Logger log = LoggerFactory.getLogger(DockerExtension.class);

    static DockerComposeRule docker = DockerComposeRule.builder()
            .pullOnStartup(true)
            .file("src/test/resources/docker-compose.yaml")
            .projectName(ProjectName.fromString("integration"))
            .shutdownStrategy(identifyShutdownStrategy())
            .waitingForService("db", HealthChecks.toHaveAllPortsOpen())
            .waitingForService("db", DockerHealthChecks.INSTANCE::checkPostgres)
            .waitingForService("quarterdeck", HealthChecks.toHaveAllPortsOpen())
            .waitingForService("quarterdeck", DockerHealthChecks.INSTANCE::checkQuarterdeck)
            .waitingForService("rabbitmq", HealthChecks.toHaveAllPortsOpen())
            .waitingForService("rabbitmq", DockerHealthChecks.INSTANCE::checkRabbitMq)
            .waitingForService("lavalink", HealthChecks.toHaveAllPortsOpen())
            .waitingForService("lavalink", DockerHealthChecks.INSTANCE::checkLavalink)
            .build();

    private static boolean hasSetup = false;

    private static ShutdownStrategy identifyShutdownStrategy() {
        String keepPostgresContainer = System.getProperty("keepDocker", "false");
        if ("true".equalsIgnoreCase(keepPostgresContainer)) {
            log.warn("Keeping the containers after the tests. Do NOT use this option in a CI environment, this "
                    + "is meant to speed up repeatedly running tests in development only.");
            return ShutdownStrategy.SKIP;
        }

        return ShutdownStrategy.GRACEFUL;
    }

    public DockerExtension() {
        //cant use AfterAllCallback#afterAll because that's too early (spring context is still alive) and leads to exception spam
        Runtime.getRuntime().addShutdownHook(new Thread(() -> docker.after(), "Docker container shutdown hook"));
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        if (hasSetup) return;
        Long timeStart = System.currentTimeMillis();
        try {
            docker.before();
        } catch (Exception e) {
            log.error("Docker failed, exiting...", e);
            System.exit(-1);
        }
        hasSetup = true;
        log.info("Started docker in " + (System.currentTimeMillis() - timeStart) + "ms");
    }

    static String execute(String command) throws IOException, InterruptedException {
        Process pr = Runtime.getRuntime().exec(command);
        try (Scanner s = new Scanner(pr.getInputStream())) {
            pr.waitFor(30, TimeUnit.SECONDS);
            return s.hasNext() ? s.next() : "";
        }
    }
}
