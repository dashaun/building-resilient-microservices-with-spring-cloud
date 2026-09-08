package com.bbqwednesday.survey;

import com.bbqwednesday.survey.client.ResultsClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
    "spring.cloud.bus.enabled=false",
    "spring.config.import=optional:file:../config-repo/survey-service.yml"
})
class HttpClientTest {
    static final AtomicInteger requests = new AtomicInteger();
    static final ExecutorService workers = Executors.newCachedThreadPool();
    static final HttpServer server = createServer();
    @Autowired ResultsClient resultsClient;

    static HttpServer createServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(workers);
            server.createContext("/sauce", exchange -> {
                try {
                    if (requests.incrementAndGet() <= 2) Thread.sleep(900);
                    byte[] body = "{\"questionId\":\"sauce\",\"answerCounts\":{\"Spicy\":1},\"totalResponses\":1}"
                            .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (IOException expectedAfterTimeout) {
                    // The client closes the first two requests after its 500 ms timeout.
                } finally {
                    exchange.close();
                }
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @DynamicPropertySource
    static void discovery(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.results-service[0].uri",
                () -> "http://localhost:" + server.getAddress().getPort());
    }

    @AfterAll static void stopServer() {
        server.stop(0);
        workers.shutdownNow();
    }

    @Test void actualHttpTimeoutsRetryThroughTheDiscoveredInterface() {
        long start = System.nanoTime();
        assertThat(resultsClient.currentTally("sauce").totalResponses()).isEqualTo(1);
        assertThat(requests.get()).isEqualTo(3);
        assertThat((System.nanoTime() - start) / 1_000_000_000.0).isLessThan(5);
    }
}
