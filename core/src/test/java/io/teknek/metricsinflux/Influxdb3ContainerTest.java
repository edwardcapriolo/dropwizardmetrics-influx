package io.teknek.metricsinflux;

import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.ScheduledReporter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class Influxdb3ContainerTest {

    @Test
    public void reporterWritesLineProtocolToInfluxdb3() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");

        try (GenericContainer<?> influx = new GenericContainer<>(DockerImageName.parse("influxdb:3.10.0-core"))
                .withExposedPorts(8181)
                .withCommand("influxdb3", "serve", "--node-id=local", "--object-store=file", "--data-dir=/tmp/influxdb3", "--without-auth")
                .waitingFor(Wait.forHttp("/health").forPort(8181).withStartupTimeout(Duration.ofSeconds(60)))) {
            influx.start();
            String host = influx.getHost();
            int port = influx.getMappedPort(8181);
            String baseUrl = "http://" + host + ":" + port;

            createDatabase(baseUrl, "recommend");

            MetricRegistry registry = new MetricRegistry();
            registry.counter("smoke.counter").inc();
            ScheduledReporter reporter = InfluxdbReporter.forRegistry(registry)
                    .protocol(new HttpInfluxdbProtocol("http", host, port, null, null, "recommend"))
                    .withAutoCreateDB(false)
                    .convertRatesTo(TimeUnit.SECONDS)
                    .convertDurationsTo(TimeUnit.MILLISECONDS)
                    .build();

            reporter.report();

            String query = query(baseUrl, "recommend", "SELECT * FROM \"smoke.counter\" WHERE time > now() - INTERVAL '10 minutes'");
            assertTrue(query.contains("\"count\":1"), query);
        }
    }

    private void createDatabase(String baseUrl, String database) throws IOException, InterruptedException {
        String body = "{\"db\":\"" + database + "\",\"retention_period\":\"32d\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v3/configure/database"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 409) {
            throw new AssertionError("Could not create database: status=" + response.statusCode() + " body=" + response.body());
        }
    }

    private String query(String baseUrl, String database, String sql) throws IOException, InterruptedException {
        String body = "{\"db\":\"" + jsonEscape(database) + "\",\"format\":\"json\",\"params\":{},\"q\":\""
                + jsonEscape(sql) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v3/query_sql"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new AssertionError("Could not query database: status=" + response.statusCode() + " body=" + response.body());
        }
        return response.body();
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
