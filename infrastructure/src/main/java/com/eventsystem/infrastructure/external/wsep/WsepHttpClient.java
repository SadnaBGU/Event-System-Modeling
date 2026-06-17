package com.eventsystem.infrastructure.external.wsep;

import com.eventsystem.infrastructure.config.WsepProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class WsepHttpClient {

    private final WsepProperties properties;
    private final HttpClient httpClient;

    public WsepHttpClient(WsepProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }

    public String post(Map<String, String> params) {
        String body = formEncode(params);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl()))
                .timeout(properties.getReadTimeout())
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new WsepCommunicationException("WSEP returned HTTP " + response.statusCode());
            }

            return response.body() == null ? "" : response.body().trim();
        } catch (IOException e) {
            throw new WsepCommunicationException("Could not reach WSEP service", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WsepCommunicationException("Interrupted while calling WSEP service", e);
        }
    }

    private String formEncode(Map<String, String> params) {
        return params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}