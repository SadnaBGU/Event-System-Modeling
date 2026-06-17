package com.eventsystem.infrastructure.external.wsep;

import java.util.Arrays;

public enum WsepAction {

    PAY("pay"),
    REFUND("refund"),
    ISSUE_TICKETS("issue_tickets");

    private final String actionType;

    WsepAction(String actionType) {
        this.actionType = actionType;
    }

    public String actionType() {
        return actionType;
    }

    public static WsepAction fromActionType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("WSEP action_type must not be blank");
        }

        return Arrays.stream(values())
                .filter(action -> action.actionType.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported WSEP action_type: " + value));
    }
}