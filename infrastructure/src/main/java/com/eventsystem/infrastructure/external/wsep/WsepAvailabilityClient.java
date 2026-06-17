package com.eventsystem.infrastructure.external.wsep;

import org.springframework.stereotype.Component;

import com.eventsystem.application.system.IExternalSystemsAvailabilityPort;
import com.eventsystem.infrastructure.external.wsep.common.WsepAction;
import com.eventsystem.infrastructure.external.wsep.common.WsepCommunicationException;
import com.eventsystem.infrastructure.external.wsep.common.WsepHttpClient;
import com.eventsystem.infrastructure.external.wsep.common.WsepResponseParser;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class WsepAvailabilityClient implements IExternalSystemsAvailabilityPort{

    private static final Logger log = LoggerFactory.getLogger(WsepAvailabilityClient.class);

    private final WsepHttpClient client;

    public WsepAvailabilityClient(WsepHttpClient client) {
        this.client = client;
    }

    public boolean isAvailable() {
        log.info("Calling WSEP handshake action to check external service availability");

        try {
            String response = client.post(Map.of(
                    "action_type", WsepAction.HANDSHAKE.actionType()));

            boolean available = WsepResponseParser.isHandshakeOk(response);

            if (available) {
                log.info("WSEP handshake succeeded");
            } else {
                log.warn("WSEP handshake returned unexpected response={}", response);
            }

            return available;

        } catch (WsepCommunicationException e) {
            log.error("WSEP handshake communication failure", e);
            throw e;
        }
    }

    @Override
    public boolean areExternalSystemsAvailable() {
        return isAvailable();
    }
}