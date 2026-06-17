package com.eventsystem.infrastructure.external.wsep.common;

import com.eventsystem.infrastructure.config.WsepProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(WsepHttpClient.class);

    private final WsepProperties properties;
    private final HttpClient httpClient;

    public WsepHttpClient(WsepProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }

    public String post(Map<String, String> params) {
        String actionType = params.get("action_type");
        String body = formEncode(params);

        log.debug("Sending WSEP HTTP POST request, action_type={}", actionType);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl()))
                .timeout(properties.getReadTimeout())
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.debug("Received WSEP HTTP response, action_type={}, status={}",
                    actionType, response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("WSEP returned non-success HTTP status, action_type={}, status={}",
                        actionType, response.statusCode());
                throw new WsepCommunicationException("WSEP returned HTTP " + response.statusCode());
            }

            String responseBody = response.body();
            if (responseBody == null || responseBody.isBlank()) {
                log.warn("WSEP returned empty response body, action_type={}", actionType);
                throw new WsepCommunicationException("WSEP returned an empty response");
            }

            return responseBody.trim();

        } catch (IOException e) {
            log.error("Could not reach WSEP service, action_type={}", actionType, e);
            throw new WsepCommunicationException("Could not reach WSEP service", e);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while calling WSEP service, action_type={}", actionType, e);
            throw new WsepCommunicationException("Interrupted while calling WSEP service", e);

        } catch (IllegalArgumentException e) {
            log.error("Invalid WSEP URL configuration: {}", properties.getBaseUrl(), e);
            throw new WsepCommunicationException("Invalid WSEP URL: " + properties.getBaseUrl(), e);
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