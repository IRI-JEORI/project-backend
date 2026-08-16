package com.nunnun.wake.service;

import com.nunnun.notification.service.DndWindowService;
import com.nunnun.routine.entity.WeeklyWakeTarget;
import com.nunnun.routine.repository.WeeklyWakeTargetRepository;
import com.nunnun.routine.service.NextWakeTargetCalculator;
import com.nunnun.wake.dto.RemainingToTargetResponse;
import com.nunnun.wake.dto.WakeGroupMemberResponse;
import com.nunnun.wake.entity.PoseMatchResult;
import com.nunnun.wake.entity.WakeGroupMember;
import com.nunnun.wake.entity.WakeProof;
import com.nunnun.wake.entity.WakeRequest;
import com.nunnun.wake.entity.WakeRequestStatus;
import com.nunnun.wake.repository.WakeProofRepository;
import com.nunnun.wake.repository.WakeRequestRepository;
import com.nunnun.wake.storage.WakeProofStorage;
import com.nunnun.wake.storage.WakeProofStorageException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WakeGroupCardService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final WeeklyWakeTargetRepository weeklyWakeTargets;
    private final WakeRequestRepository wakeRequests;
    private final WakeProofRepository wakeProofs;
    private final NextWakeTargetCalculator nextWakeTargetCalculator;
    private final WakeGroupCardStateCalculator stateCalculator;
    private final WakeEligibilityPolicy eligibilityPolicy;
    private final DndWindowService dndWindowService;
    private final WakeProofStorage storage;
    private final Clock clock;

    public WakeGroupCardService(
            WeeklyWakeTargetRepository weeklyWakeTargets,
            WakeRequestRepository wakeRequests,
            WakeProofRepository wakeProofs,
            NextWakeTargetCalculator nextWakeTargetCalculator,
            WakeGroupCardStateCalculator stateCalculator,
            WakeEligibilityPolicy eligibilityPolicy,
            DndWindowService dndWindowService,
            WakeProofStorage storage,
            Clock clock
    ) {
        this.weeklyWakeTargets = weeklyWakeTargets;
        this.wakeRequests = wakeRequests;
        this.wakeProofs = wakeProofs;
        this.nextWakeTargetCalculator = nextWakeTargetCalculator;
        this.stateCalculator = stateCalculator;
        this.eligibilityPolicy = eligibilityPolicy;
        this.dndWindowService = dndWindowService;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WakeGroupMemberResponse> createCards(
            Long groupId,
            Long currentUserId,
            List<WakeGroupMember> members
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        ZonedDateTime zonedNow = ZonedDateTime.now(clock).withZoneSameInstant(BUSINESS_ZONE);
        List<Long> userIds = members.stream().map(member -> member.getUser().getId()).toList();
        Map<Long, List<WeeklyWakeTarget>> targetsByUser = weeklyWakeTargets.findAllByUserIdIn(userIds)
                .stream().collect(Collectors.groupingBy(target -> target.getUser().getId()));
        Map<Long, List<WakeRequest>> requestsByReceiver = wakeRequests.findAllByWakeGroupId(groupId)
                .stream().collect(Collectors.groupingBy(request -> request.getReceiver().getId()));
        Map<Long, WakeProof> proofsByRequest = wakeProofs.findAllByWakeRequestWakeGroupId(groupId)
                .stream().collect(Collectors.toMap(
                        proof -> proof.getWakeRequest().getId(),
                        Function.identity()
                ));
        Set<Long> dndActiveUserIds = dndWindowService.findDndActiveUserIds(userIds, zonedNow);

        return members.stream()
                .map(member -> createCard(
                        member,
                        currentUserId,
                        targetsByUser.getOrDefault(member.getUser().getId(), List.of()),
                        requestsByReceiver.getOrDefault(member.getUser().getId(), List.of()),
                        proofsByRequest,
                        dndActiveUserIds.contains(member.getUser().getId()),
                        now
                ))
                .toList();
    }

    private WakeGroupMemberResponse createCard(
            WakeGroupMember member,
            Long currentUserId,
            List<WeeklyWakeTarget> targets,
            List<WakeRequest> requests,
            Map<Long, WakeProof> proofsByRequest,
            boolean dndActive,
            LocalDateTime now
    ) {
        LocalDateTime todayTargetAt = targets.stream()
                .filter(target -> target.getDayOfWeek() == now.getDayOfWeek())
                .findFirst()
                .map(target -> LocalDateTime.of(now.toLocalDate(), target.getTargetWakeTime()))
                .orElse(null);
        LocalDateTime nextTargetAt = nextWakeTargetCalculator.calculate(targets, now).orElse(null);
        WakeProof latestSuccessProof = latestSuccessProof(requests, proofsByRequest, now);
        LocalDateTime latestSuccessAt = latestSuccessProof == null ? null : latestSuccessProof.getVerifiedAt();
        LocalDateTime latestNeedsHelpAt = requests.stream()
                .filter(request -> request.getStatus() == WakeRequestStatus.NEEDS_HELP)
                .map(request -> {
                    WakeProof proof = proofsByRequest.get(request.getId());
                    return proof == null ? request.getRequestedAt() : proof.getSubmittedAt();
                })
                .filter(requestedAt -> !requestedAt.isAfter(now))
                .max(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime nextTargetAfterSuccessAt = latestSuccessAt == null
                ? null
                : nextWakeTargetCalculator.calculate(targets, latestSuccessAt).orElse(null);
        WakeGroupCardStateCalculator.Result state = stateCalculator.calculate(
                new WakeGroupCardStateCalculator.Facts(
                        now,
                        todayTargetAt,
                        latestSuccessAt,
                        latestNeedsHelpAt,
                        nextTargetAfterSuccessAt
                )
        );
        WakeEligibilityPolicy.Result eligibility = eligibilityPolicy.evaluate(dndActive, latestSuccessAt, now);
        RemainingToTargetResponse remaining = stateCalculator.remainingToTarget(now, todayTargetAt);
        WakeProof displayedProof = state.actualWakeAt() == null ? null : latestSuccessProof;

        return new WakeGroupMemberResponse(
                member.getUser().getId(),
                member.getUser().getNickname(),
                member.getUser().getAvatarUrl(),
                member.getUser().getId().equals(currentUserId),
                todayTargetAt == null ? null : todayTargetAt.toLocalTime().format(TIME_FORMATTER),
                atBusinessOffset(nextTargetAt),
                remaining,
                state.state(),
                state.actualWakeAt() == null ? null : state.actualWakeAt().toLocalTime().format(TIME_FORMATTER),
                proofImageUrl(displayedProof, now),
                displayedProof == null ? null : atBusinessOffset(displayedProof.getExpiresAt()),
                eligibility.canWake(),
                eligibility.blockReason(),
                atBusinessOffset(eligibility.wakeAvailableAt())
        );
    }

    private WakeProof latestSuccessProof(
            Collection<WakeRequest> requests,
            Map<Long, WakeProof> proofsByRequest,
            LocalDateTime now
    ) {
        return requests.stream()
                .filter(request -> request.getStatus() == WakeRequestStatus.VERIFIED)
                .map(request -> proofsByRequest.get(request.getId()))
                .filter(proof -> proof != null
                        && proof.getPoseMatchResult() == PoseMatchResult.SUCCESS
                        && proof.getVerifiedAt() != null
                        && !proof.getVerifiedAt().isAfter(now))
                .max(Comparator.comparing(WakeProof::getVerifiedAt))
                .orElse(null);
    }

    private String proofImageUrl(WakeProof proof, LocalDateTime now) {
        if (proof == null
                || proof.getImageObjectKey() == null
                || proof.getExpiresAt() == null
                || !proof.getExpiresAt().isAfter(now)) {
            return null;
        }
        try {
            return storage.createReadUrl(
                    proof.getImageObjectKey(),
                    Duration.between(now, proof.getExpiresAt())
            );
        } catch (WakeProofStorageException exception) {
            return null;
        }
    }

    private OffsetDateTime atBusinessOffset(LocalDateTime value) {
        return value == null ? null : value.atZone(BUSINESS_ZONE).toOffsetDateTime();
    }
}
