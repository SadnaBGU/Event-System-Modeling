package com.eventsystem.application.event;

public interface IEventPermissionChecker {

    boolean canManageEvents(String actorId, String companyId);
}