package com.eventsystem.infrastructure.external.wsep.common;

/**
 * WSEP answered with a successful HTTP status but an empty/blank body. This is an
 * <em>unexpected</em> application-level response (distinct from a connectivity failure):
 * the server was reached, yet returned nothing we can interpret as a result. It extends
 * {@link WsepCommunicationException} so existing callers that treat any WSEP communication
 * problem as a failure keep working unchanged, while flows that care (e.g. the payment
 * adapter) can single it out as an "unexpected response".
 */
public class WsepEmptyResponseException extends WsepCommunicationException {

    public WsepEmptyResponseException(String message) {
        super(message);
    }
}
