package com.eventsystem.application.event;

public interface EventPermissionChecker {

    boolean canManageEvents(String actorId, String companyId);
}