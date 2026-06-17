package com.eventsystem.infrastructure.external.wsep;

import com.eventsystem.infrastructure.config.WsepProperties;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WsepTestServer implements AutoCloseable {

    private final HttpServer server;
    private final ArrayDeque<Response> responses = new ArrayDeque<>();
    private final List<RecordedRequest> requests = new ArrayList<>();

    public WsepTestServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(0), 0);

        this.server.createContext("/wsep", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            requests.add(new RecordedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    body,
                    parseFormBody(body)
            ));

            Response response = responses.isEmpty()
                    ? new Response(500, "No response enqueued")
                    : responses.removeFirst();

            byte[] responseBytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(response.status(), responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        });

        this.server.start();
    }

    public void enqueue(int status, String body) {
        responses.addLast(new Response(status, body));
    }

    public WsepProperties properties() {
        WsepProperties properties = new WsepProperties();
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort() + "/wsep");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(1));
        return properties;
    }

    public int requestCount() {
        return requests.size();
    }

    public RecordedRequest request(int index) {
        return requests.get(index);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static Map<String, String> parseFormBody(String body) {
        Map<String, String> result = new LinkedHashMap<>();

        if (body == null || body.isBlank()) {
            return result;
        }

        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            String value = parts.length == 2 ? decode(parts[1]) : "";
            result.put(key, value);
        }

        return result;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private record Response(int status, String body) {
    }

    public record RecordedRequest(
            String method,
            String contentType,
            String rawBody,
            Map<String, String> form
    ) {
    }
}