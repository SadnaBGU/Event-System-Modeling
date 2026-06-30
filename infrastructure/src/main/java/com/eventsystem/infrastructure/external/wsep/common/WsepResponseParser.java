package com.eventsystem.infrastructure.external.wsep.common;

public final class WsepResponseParser {

    private WsepResponseParser() {}

    public static boolean isFailure(String response) {
        return response == null
                || response.isBlank()
                || response.trim().equals("-1");
    }

    /**
     * A successful WSEP {@code pay} returns a numeric transaction id. Anything that is
     * neither a transaction id nor the {@code -1} decline is an unexpected response and
     * must not be treated as a successful payment.
     */
    public static boolean isPayTransactionId(String response) {
        return response != null && response.trim().matches("\\d+");
    }

    public static boolean isSuccessOne(String response) {
        return response != null && response.trim().equals("1");
    }

    public static boolean isHandshakeOk(String response) {
        return response != null && response.trim().equalsIgnoreCase("OK");
    }
}