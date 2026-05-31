# Admin Member Suspension — Use Cases & UATs (Version 2)
## UC II.6.7 — Suspend Member

**Actor:** System Administrator (visiting the market)
**Preconditions:** Administrator is logged in. Target member exists and is ACTIVE or already SUSPENDED.
**Trigger:** Admin chooses to suspend a member's activity.

### Main Flow
1. Admin provides the target member's ID and an optional duration (in days).
2. System validates the actor is an administrator.
3. System loads the target member.
4. System records a `Suspension` on the member (with current timestamp and provided duration; no duration = permanent).
5. Member status changes to `SUSPENDED`.
6. System saves the updated member.
7. System confirms the operation succeeded.

### Alternate Flows
- **A1 — Actor is not an administrator:** System rejects with 403 Forbidden.
- **A2 — Target member not found:** System rejects with 400 Bad Request.
- **A3 — Target member is CANCELLED:** System rejects with 400 Bad Request (cannot suspend a cancelled member).

### Postconditions
- Member status is `SUSPENDED`.
- A `Suspension` record exists on the member with `suspendedAt` = now, `duration` = requested duration (null if permanent).
- Suspended member can only perform read (view) operations in the system.

---

## UC II.6.8 — Cancel Member Suspension

**Actor:** System Administrator (visiting the market)
**Preconditions:** Administrator is logged in. Target member exists and is currently `SUSPENDED`.
**Trigger:** Admin chooses to lift a member's suspension.

### Main Flow
1. Admin provides the target member's ID.
2. System validates the actor is an administrator.
3. System loads the target member.
4. System clears the `Suspension` record and sets member status back to `ACTIVE`.
5. System saves the updated member.
6. System confirms the operation succeeded.

### Alternate Flows
- **A1 — Actor is not an administrator:** System rejects with 403 Forbidden.
- **A2 — Target member not found:** System rejects with 400 Bad Request.
- **A3 — Target member is not SUSPENDED:** System rejects with 400 Bad Request.

### Postconditions
- Member status is `ACTIVE`.
- No `Suspension` record exists on the member.

---

## UC II.6.9 — View Member Suspensions

**Actor:** System Administrator (visiting the market)
**Preconditions:** Administrator is logged in.
**Trigger:** Admin requests to view all current suspensions in the system.

### Main Flow
1. Admin requests the list of suspensions.
2. System validates the actor is an administrator.
3. System retrieves all members with status `SUSPENDED`.
4. For each suspended member, system returns:
   - Member ID
   - Username
   - Suspension date (`suspendedAt`)
   - Duration (e.g. "PT168H" for 7 days, or "PERMANENT")
   - End date (`endsAt`) — null if permanent
5. System returns the list to the admin.

### Alternate Flows
- **A1 — Actor is not an administrator:** System rejects with 403 Forbidden.
- **A2 — No suspensions exist:** System returns an empty list (not an error).

### Postconditions
- No state change. Read-only operation.

---

# UAT — Admin Member Suspension

## UAT-S01 — Suspend member for a fixed duration (happy path)
**Given** an admin and an ACTIVE member  
**When** admin calls `POST /api/admin/members/{id}/suspend` with `{ "durationDays": 7 }`  
**Then** response is 200 OK, member status is SUSPENDED, suspension duration is 7 days, endsAt = suspendedAt + 7 days

## UAT-S02 — Suspend member permanently (no body)
**Given** an admin and an ACTIVE member  
**When** admin calls `POST /api/admin/members/{id}/suspend` with no body  
**Then** response is 200 OK, member status is SUSPENDED, suspension isPermanent = true, endsAt = null

## UAT-S03 — Suspend member permanently (durationDays: 0)
**Given** an admin and an ACTIVE member  
**When** admin calls `POST /api/admin/members/{id}/suspend` with `{ "durationDays": 0 }`  
**Then** response is 200 OK, suspension isPermanent = true

## UAT-S04 — Non-admin cannot suspend
**Given** a regular (non-admin) member  
**When** they call `POST /api/admin/members/{id}/suspend`  
**Then** response is 403 Forbidden

## UAT-S05 — Cannot suspend unknown member
**Given** an admin  
**When** they call `POST /api/admin/members/unknown-id/suspend`  
**Then** response is 400 Bad Request

## UAT-S06 — Cannot suspend a CANCELLED member
**Given** an admin and a CANCELLED member  
**When** admin calls `POST /api/admin/members/{id}/suspend`  
**Then** response is 400 Bad Request

## UAT-S07 — Suspended member cannot modify profile
**Given** a SUSPENDED member  
**When** they attempt to update their personal details  
**Then** the operation is rejected (400 Bad Request or 403 Forbidden)

## UAT-S08 — Unsuspend a temporarily suspended member (happy path)
**Given** an admin and a SUSPENDED member  
**When** admin calls `DELETE /api/admin/members/{id}/suspend`  
**Then** response is 200 OK, member status is ACTIVE, no suspension record exists

## UAT-S09 — Unsuspend a permanently suspended member
**Given** an admin and a permanently SUSPENDED member  
**When** admin calls `DELETE /api/admin/members/{id}/suspend`  
**Then** response is 200 OK, member status is ACTIVE

## UAT-S10 — Cannot unsuspend an ACTIVE member
**Given** an admin and an ACTIVE member  
**When** admin calls `DELETE /api/admin/members/{id}/suspend`  
**Then** response is 400 Bad Request

## UAT-S11 — Non-admin cannot unsuspend
**Given** a regular member  
**When** they call `DELETE /api/admin/members/{id}/suspend`  
**Then** response is 403 Forbidden

## UAT-S12 — View suspensions returns all suspended members
**Given** an admin, member A (SUSPENDED, 7 days), member B (SUSPENDED, permanent), member C (ACTIVE)  
**When** admin calls `GET /api/admin/suspensions`  
**Then** response is 200 OK with 2 entries — A and B — each showing memberId, username, suspendedAt, duration, endsAt

## UAT-S13 — View suspensions returns empty list when none exist
**Given** an admin and no suspended members  
**When** admin calls `GET /api/admin/suspensions`  
**Then** response is 200 OK with an empty array

## UAT-S14 — Non-admin cannot view suspensions
**Given** a regular member  
**When** they call `GET /api/admin/suspensions`  
**Then** response is 403 Forbidden

## UAT-S15 — Temporary suspension: expired member treated as not suspended
**Given** a member suspended for 1 day, 2 days ago  
**When** `isSuspendedAt(now)` is checked  
**Then** returns false (suspension has expired)
