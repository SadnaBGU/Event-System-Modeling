package com.eventsystem.infrastructure.external.wsep.common;

public enum WsepAction {

    HANDSHAKE("handshake"),
    PAY("pay"),
    REFUND("refund"),
    ISSUE_TICKET("issue_ticket"),
    CANCEL_TICKET("cancel_ticket");

    private final String actionType;

    WsepAction(String actionType) {
        this.actionType = actionType;
    }

    public String actionType() {
        return actionType;
    }

    // public static WsepAction fromActionType(String value) {
    //     if (value == null || value.isBlank()) {
    //         throw new IllegalArgumentException("WSEP action_type must not be blank");
    //     }

    //     return Arrays.stream(values())
    //             .filter(action -> action.actionType.equalsIgnoreCase(value.trim()))
    //             .findFirst()
    //             .orElseThrow(() -> new IllegalArgumentException("Unsupported WSEP action_type: " + value));
    // }
}