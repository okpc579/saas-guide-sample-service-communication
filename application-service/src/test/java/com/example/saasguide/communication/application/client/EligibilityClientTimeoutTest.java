package com.example.saasguide.communication.application.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.saasguide.communication.application.context.ServiceRequestContext;
import com.example.saasguide.communication.application.error.EligibilityServiceUnavailableException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class EligibilityClientTimeoutTest {
    @Test
    void readTimeoutStopsTheCallWithinConfiguredBound() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/eligibilities/A", exchange -> {
            try {
                Thread.sleep(1_000);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofMillis(100));
            factory.setReadTimeout(Duration.ofMillis(100));
            RestClient restClient = RestClient.builder()
                    .baseUrl("http://localhost:" + server.getAddress().getPort())
                    .requestFactory(factory)
                    .build();
            EligibilityClient client = new EligibilityClient(restClient);
            long started = System.nanoTime();

            assertThatThrownBy(() -> client.checkEligibility(
                    "A", new ServiceRequestContext("tenant", "request", "trace")))
                    .isInstanceOf(EligibilityServiceUnavailableException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
        } finally {
            server.stop(0);
        }
    }
}
