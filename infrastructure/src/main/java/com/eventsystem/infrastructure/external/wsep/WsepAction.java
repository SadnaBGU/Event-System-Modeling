package com.eventsystem.infrastructure.external.wsep;

public enum WsepAction {
    ACTION_PAY,
    ACTION_REFUND,
    ACTION_ISSUE_TICKETS;


    public String actionString() {
        return this == ACTION_PAY ? "pay"
                 : this == ACTION_REFUND ? "refund"
                 : this == ACTION_ISSUE_TICKETS ? "issue_tickets"
                 : null;
    }

    public WsepAction fromString(String str) {
        str = str.toLowerCase().trim();
        return str.equals("pay") ? ACTION_PAY 
                : str.equals("refund") ? ACTION_REFUND
                : str.equals("issue_tickets") ? ACTION_ISSUE_TICKETS
                : null;
    }
}
