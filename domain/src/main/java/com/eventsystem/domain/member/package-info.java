/**
 * Member aggregate.
 *
 * <p>Owns: {@code Member} aggregate root + {@code Notification} entity + value objects
 * ({@code MemberId}, {@code PersonalDetails}, {@code HashedCredentials}) + enums
 * ({@code MemberStatus}, {@code NotificationType}) + {@code MemberRepository} port.
 *
 * <p>See: {@code docs/1_Platform_Member.mmd}.
 */
package com.eventsystem.domain.member;
