package com.eventsystem.infrastructure.external.wsep.common;

public final class WsepResponseParser {

    private WsepResponseParser() {}

    public static boolean isFailure(String response) {
        return response == null
                || response.isBlank()
                || response.trim().equals("-1");
    }

    public static boolean isSuccessOne(String response) {
        return response != null && response.trim().equals("1");
    }

    public static boolean isHandshakeOk(String response) {
        return response != null && response.trim().equalsIgnoreCase("OK");
    }
}