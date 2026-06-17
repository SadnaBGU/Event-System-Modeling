package com.eventsystem.infrastructure.external.wsep;

import org.springframework.stereotype.Component;

import com.eventsystem.infrastructure.external.wsep.common.WsepAction;
import com.eventsystem.infrastructure.external.wsep.common.WsepHttpClient;
import com.eventsystem.infrastructure.external.wsep.common.WsepResponseParser;

import java.util.Map;

@Component
public class WsepAvailabilityClient {

    private final WsepHttpClient client;

    public WsepAvailabilityClient(WsepHttpClient client) {
        this.client = client;
    }

    public boolean isAvailable() {
        String response = client.post(Map.of(
                "action_type", WsepAction.HANDSHAKE.actionType()
        ));

        return WsepResponseParser.isHandshakeOk(response);
    }
}