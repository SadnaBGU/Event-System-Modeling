package com.eventsystem.application.appexceptions;

import com.eventsystem.domain.policy.shared.PolicyValidationResult;

public class OrderViolatesPolicyException extends RuntimeException {
    private String originalErrMsg = "";

    public OrderViolatesPolicyException(String message) {
        super(message);
    }

    public OrderViolatesPolicyException(PolicyValidationResult pvr, String message) {
        super(message);
        originalErrMsg = pvr.reason();
    }

    public String getOriginalMsg() {
        return originalErrMsg;
    }
    
}
