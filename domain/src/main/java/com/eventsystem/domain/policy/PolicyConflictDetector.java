package com.eventsystem.domain.policy;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.policy.basic.AfterDatePolicy;
import com.eventsystem.domain.policy.basic.MaxTicketPolicy;
import com.eventsystem.domain.policy.basic.MinTicketPolicy;
import com.eventsystem.domain.policy.basic.UntilDatePolicy;
import com.eventsystem.domain.policy.composite.AndPolicy;
import com.eventsystem.domain.policy.composite.ICompositePolicy;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public final class PolicyConflictDetector {

    private PolicyConflictDetector() {
        throw new UnsupportedOperationException("PolicyConflictDetector is a utility class");
    }

    /**
     * Checks whether a single policy tree is internally contradictory.
     *
     * Examples:
     * AND(MIN_TICKETS(5), MAX_TICKETS(4)) is impossible.
     * AND(AFTER_DATE(2026-06-10), UNTIL_DATE(2026-06-01)) is impossible.
     *
     * OR is invalid only if all its branches are internally conflicting.
     */
    public static PolicyValidationResult detectInnerConflicts(IPolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");

        ConflictAnalysis analysis = analyze(policy);

        if (analysis.conflicting()) {
            return PolicyValidationResult.failure(analysis.reason());
        }

        return PolicyValidationResult.success();
    }

    /**
     * Checks whether a limiting policy contradicts a requiring policy.
     *
     * Main current use:
     * limitingPolicy = purchase policy, for example MAX_TICKETS(4)
     * requiringPolicy = discount condition, for example MIN_TICKETS(5)
     *
     * This method is intentionally separate from detectInnerConflicts().
     */
    public static PolicyValidationResult detectConflictBetween(
            IPolicy limitingPolicy,
            IPolicy requiringPolicy
    ) {
        Objects.requireNonNull(limitingPolicy, "limitingPolicy must not be null");
        Objects.requireNonNull(requiringPolicy, "requiringPolicy must not be null");

        PolicyValidationResult limitingInner = detectInnerConflicts(limitingPolicy);
        if (!limitingInner.isSuccess()) {
            return PolicyValidationResult.failure(
                    "Conflict inside limiting policy: " + limitingInner.reason()
            );
        }

        PolicyValidationResult requiringInner = detectInnerConflicts(requiringPolicy);
        if (!requiringInner.isSuccess()) {
            return PolicyValidationResult.failure(
                    "Conflict inside requiring policy: " + requiringInner.reason()
            );
        }

        OptionalInt maxTicketsAllowed = extractMaxTicketsAllowed(limitingPolicy);

        if (maxTicketsAllowed.isPresent()
                && requiresMoreTicketsThanAllowed(requiringPolicy, maxTicketsAllowed.getAsInt())) {
            return PolicyValidationResult.failure(
                    "Policy conflict: requiring policy requires more tickets than limiting policy allows. "
                            + "Limiting policy allows at most "
                            + maxTicketsAllowed.getAsInt()
                            + " tickets"
            );
        }

        return PolicyValidationResult.success();
    }

    private static ConflictAnalysis analyze(IPolicy policy) {
        if (policy == null) {
            return ConflictAnalysis.conflict("Policy cannot be null");
        }

        return switch (policy.type()) {
            case MIN_TICKETS -> {
                MinTicketPolicy minTicketPolicy = (MinTicketPolicy) policy;
                yield ConflictAnalysis.valid(
                        ConstraintSet.withMinTickets(minTicketPolicy.minTickets())
                );
            }

            case MAX_TICKETS -> {
                MaxTicketPolicy maxTicketPolicy = (MaxTicketPolicy) policy;
                yield ConflictAnalysis.valid(
                        ConstraintSet.withMaxTickets(maxTicketPolicy.maxTickets())
                );
            }

            case AFTER_DATE -> {
                AfterDatePolicy afterDatePolicy = (AfterDatePolicy) policy;
                yield ConflictAnalysis.valid(
                        ConstraintSet.withAfterDate(afterDatePolicy.deadlineDate())
                );
            }

            case UNTIL_DATE -> {
                UntilDatePolicy untilDatePolicy = (UntilDatePolicy) policy;
                yield ConflictAnalysis.valid(
                        ConstraintSet.withUntilDate(untilDatePolicy.deadlineDate())
                );
            }

            case AND -> analyzeAnd(policy);

            case OR -> analyzeOr(policy);

            /*
             * ZONE_SPECIFIC_0_FAIL:
             * If no affected tickets exist, the zone policy fails.
             * For inner-conflict purposes, the wrapped child still matters,
             * so we analyze the child as a mandatory branch.
             */
            case ZONE_SPECIFIC_0_FAIL -> analyzeZoneSpecificFail(policy);

            /*
             * ZONE_SPECIFIC_0_PASS:
             * If no affected tickets exist, the policy can pass without its child.
             * Therefore, even if the child is internally impossible, the wrapper as a whole
             * is not necessarily impossible globally.
             */
            case ZONE_SPECIFIC_0_PASS -> ConflictAnalysis.valid(ConstraintSet.empty());

            case NO_SINGLE_EMPTY_SEAT -> ConflictAnalysis.conflict(
                    "NoSingleEmptySeatPolicy is not supported because seat-neighbor layout data is missing"
            );

            /*
             * ALWAYS_TRUE, NEVER_ALLOW, MIN_AGE, CODE, UNKNOWN:
             * None creates a structural contradiction by itself.
             *
             * NEVER_ALLOW is intentionally not treated as a conflict.
             * It is a valid policy that always rejects, not a malformed policy.
             */
            default -> ConflictAnalysis.valid(ConstraintSet.empty());
        };
    }

    private static ConflictAnalysis analyzeAnd(IPolicy policy) {
        if (!(policy instanceof ICompositePolicy compositePolicy)) {
            return ConflictAnalysis.conflict("AND policy must expose children");
        }

        List<IPolicy> children = compositePolicy.children();

        if (children == null || children.isEmpty()) {
            return ConflictAnalysis.conflict("AND policy must contain at least one child policy");
        }

        ConstraintSet merged = ConstraintSet.empty();

        for (IPolicy child : children) {
            ConflictAnalysis childAnalysis = analyze(child);

            if (childAnalysis.conflicting()) {
                return childAnalysis;
            }

            merged = merged.mergeWithAnd(childAnalysis.constraints());

            Optional<String> contradiction = merged.findContradiction();
            if (contradiction.isPresent()) {
                return ConflictAnalysis.conflict(contradiction.get());
            }
        }

        return ConflictAnalysis.valid(merged);
    }

    private static ConflictAnalysis analyzeOr(IPolicy policy) {
        if (!(policy instanceof ICompositePolicy compositePolicy)) {
            return ConflictAnalysis.conflict("OR policy must expose children");
        }

        List<IPolicy> children = compositePolicy.children();

        if (children == null || children.isEmpty()) {
            return ConflictAnalysis.conflict("OR policy must contain at least one child policy");
        }

        int conflictingBranches = 0;
        String lastConflictReason = null;

        for (IPolicy child : children) {
            ConflictAnalysis childAnalysis = analyze(child);

            if (!childAnalysis.conflicting()) {
                /*
                 * At least one branch can be valid, so the OR as a whole is valid.
                 * We cannot merge OR constraints as global constraints because only one branch
                 * must pass.
                 */
                return ConflictAnalysis.valid(ConstraintSet.empty());
            }

            conflictingBranches++;
            lastConflictReason = childAnalysis.reason();
        }

        return ConflictAnalysis.conflict(
                "OR policy is impossible because all "
                        + conflictingBranches
                        + " branches conflict. Last conflict: "
                        + lastConflictReason
        );
    }

    private static ConflictAnalysis analyzeZoneSpecificFail(IPolicy policy) {
        if (!(policy instanceof ICompositePolicy compositePolicy)) {
            return ConflictAnalysis.conflict("Zone-specific policy must expose children");
        }

        List<IPolicy> children = compositePolicy.children();

        if (children == null || children.isEmpty()) {
            return ConflictAnalysis.conflict("Zone-specific policy must contain a child policy");
        }

        /*
         * Your ZoneSpecificPolicy returns one child, but this also works if that changes.
         * Since ZONE_SPECIFIC_0_FAIL is mandatory enough, analyze it like AND.
         */
        ConstraintSet merged = ConstraintSet.empty();

        for (IPolicy child : children) {
            ConflictAnalysis childAnalysis = analyze(child);

            if (childAnalysis.conflicting()) {
                return childAnalysis;
            }

            merged = merged.mergeWithAnd(childAnalysis.constraints());

            Optional<String> contradiction = merged.findContradiction();
            if (contradiction.isPresent()) {
                return ConflictAnalysis.conflict(contradiction.get());
            }
        }

        return ConflictAnalysis.valid(merged);
    }

    private static OptionalInt extractMaxTicketsAllowed(IPolicy policy) {
        ConflictAnalysis analysis = analyze(policy);

        if (analysis.conflicting()) {
            return OptionalInt.empty();
        }

        return analysis.constraints().maxTickets();
    }

    private static boolean requiresMoreTicketsThanAllowed(IPolicy policy, int maxTicketsAllowed) {
        if (policy == null) {
            return false;
        }

        return switch (policy.type()) {
            case MIN_TICKETS ->
                    ((MinTicketPolicy) policy).minTickets() > maxTicketsAllowed;

            case AND ->
                    policy instanceof ICompositePolicy compositePolicy
                            && compositePolicy.children()
                            .stream()
                            .anyMatch(child -> requiresMoreTicketsThanAllowed(child, maxTicketsAllowed));

            case OR ->
                    policy instanceof ICompositePolicy compositePolicy
                            && !compositePolicy.children().isEmpty()
                            && compositePolicy.children()
                            .stream()
                            .allMatch(child -> requiresMoreTicketsThanAllowed(child, maxTicketsAllowed));

            case ZONE_SPECIFIC_0_FAIL ->
                    policy instanceof ICompositePolicy compositePolicy
                            && compositePolicy.children()
                            .stream()
                            .anyMatch(child -> requiresMoreTicketsThanAllowed(child, maxTicketsAllowed));

            case ZONE_SPECIFIC_0_PASS ->
                    false;

            default ->
                    false;
        };
    }

    private record ConflictAnalysis(
            boolean conflicting,
            String reason,
            ConstraintSet constraints
    ) {

        private ConflictAnalysis {
            Objects.requireNonNull(constraints, "constraints must not be null");

            if (conflicting && (reason == null || reason.isBlank())) {
                throw new IllegalArgumentException("Conflict reason must be provided");
            }
        }

        private static ConflictAnalysis valid(ConstraintSet constraints) {
            return new ConflictAnalysis(false, null, constraints);
        }

        private static ConflictAnalysis conflict(String reason) {
            return new ConflictAnalysis(true, Objects.requireNonNull(reason), ConstraintSet.empty());
        }
    }

    private record ConstraintSet(
            OptionalInt minTickets,
            OptionalInt maxTickets,
            Optional<LocalDate> afterDate,
            Optional<LocalDate> untilDate
    ) {

        private ConstraintSet {
            Objects.requireNonNull(minTickets, "minTickets must not be null");
            Objects.requireNonNull(maxTickets, "maxTickets must not be null");
            Objects.requireNonNull(afterDate, "afterDate must not be null");
            Objects.requireNonNull(untilDate, "untilDate must not be null");
        }

        private static ConstraintSet empty() {
            return new ConstraintSet(
                    OptionalInt.empty(),
                    OptionalInt.empty(),
                    Optional.empty(),
                    Optional.empty()
            );
        }

        private static ConstraintSet withMinTickets(int value) {
            return new ConstraintSet(
                    OptionalInt.of(value),
                    OptionalInt.empty(),
                    Optional.empty(),
                    Optional.empty()
            );
        }

        private static ConstraintSet withMaxTickets(int value) {
            return new ConstraintSet(
                    OptionalInt.empty(),
                    OptionalInt.of(value),
                    Optional.empty(),
                    Optional.empty()
            );
        }

        private static ConstraintSet withAfterDate(LocalDate value) {
            return new ConstraintSet(
                    OptionalInt.empty(),
                    OptionalInt.empty(),
                    Optional.of(value),
                    Optional.empty()
            );
        }

        private static ConstraintSet withUntilDate(LocalDate value) {
            return new ConstraintSet(
                    OptionalInt.empty(),
                    OptionalInt.empty(),
                    Optional.empty(),
                    Optional.of(value)
            );
        }

        private ConstraintSet mergeWithAnd(ConstraintSet other) {
            Objects.requireNonNull(other, "other must not be null");

            return new ConstraintSet(
                    strongestMinTickets(this.minTickets, other.minTickets),
                    strongestMaxTickets(this.maxTickets, other.maxTickets),
                    strongestAfterDate(this.afterDate, other.afterDate),
                    strongestUntilDate(this.untilDate, other.untilDate)
            );
        }

        private Optional<String> findContradiction() {
            if (minTickets.isPresent()
                    && maxTickets.isPresent()
                    && minTickets.getAsInt() > maxTickets.getAsInt()) {
                return Optional.of(
                        "Policy conflict: minimum tickets "
                                + minTickets.getAsInt()
                                + " is greater than maximum tickets "
                                + maxTickets.getAsInt()
                );
            }

            if (afterDate.isPresent()
                    && untilDate.isPresent()
                    && !afterDate.get().isBefore(untilDate.get())) {
                return Optional.of(
                        "Policy conflict: impossible date window. Policy requires purchase after "
                                + afterDate.get()
                                + " and until "
                                + untilDate.get()
                );
            }

            return Optional.empty();
        }

        private static OptionalInt strongestMinTickets(OptionalInt first, OptionalInt second) {
            if (first.isEmpty()) {
                return second;
            }

            if (second.isEmpty()) {
                return first;
            }

            return OptionalInt.of(Math.max(first.getAsInt(), second.getAsInt()));
        }

        private static OptionalInt strongestMaxTickets(OptionalInt first, OptionalInt second) {
            if (first.isEmpty()) {
                return second;
            }

            if (second.isEmpty()) {
                return first;
            }

            return OptionalInt.of(Math.min(first.getAsInt(), second.getAsInt()));
        }

        private static Optional<LocalDate> strongestAfterDate(
                Optional<LocalDate> first,
                Optional<LocalDate> second
        ) {
            if (first.isEmpty()) {
                return second;
            }

            if (second.isEmpty()) {
                return first;
            }

            return first.get().isAfter(second.get()) ? first : second;
        }

        private static Optional<LocalDate> strongestUntilDate(
                Optional<LocalDate> first,
                Optional<LocalDate> second
        ) {
            if (first.isEmpty()) {
                return second;
            }

            if (second.isEmpty()) {
                return first;
            }

            return first.get().isBefore(second.get()) ? first : second;
        }
    }

    
    public static void requireValidPolicy(IPolicy policy) {
        Objects.requireNonNull(policy, "eventIds must not be null");
        if (!policy.isValidPolicy()) {
            throw new PolicyException("Invalid policy for purchase policy");
        }
        PolicyValidationResult conflictResult = PolicyConflictDetector.detectInnerConflicts(policy);
        if (!conflictResult.isSuccess()) {
            throw new PolicyException(
                    "Invalid purchase policy: " + conflictResult.reason()
            );
        }
    }

    public static void requireValidPolicy(List<IPolicy> policies) {
        Objects.requireNonNull(policies, "eventIds must not be null");
        if (policies.isEmpty()) {
            throw new PolicyException("Purchahse Policy cannot be empty or null");
        }
        if (policies.stream().anyMatch(Objects::isNull)) {
            throw new PolicyException("PurchasePolicy cannot contain null policies");
        }
        requireValidPolicy(new AndPolicy(policies));
    }
}