package com.nunnun.my.service;

import com.nunnun.my.dto.MyStatsResponse;
import com.nunnun.wake.entity.WakeRequestStatus;
import com.nunnun.wake.repository.WakeProofRepository;
import com.nunnun.wake.repository.WakeRequestRepository;
import com.nunnun.wake.repository.WakeSuccessProjection;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MyStatsService {

    private final WakeRequestRepository wakeRequestRepository;
    private final WakeProofRepository wakeProofRepository;
    private final Clock clock;

    public MyStatsService(
            WakeRequestRepository wakeRequestRepository,
            WakeProofRepository wakeProofRepository,
            Clock clock
    ) {
        this.wakeRequestRepository = wakeRequestRepository;
        this.wakeProofRepository = wakeProofRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MyStatsResponse getStats(Long userId) {
        long verified = wakeRequestRepository.countByReceiverIdAndStatus(userId, WakeRequestStatus.VERIFIED);
        long needsHelp = wakeRequestRepository.countByReceiverIdAndStatus(userId, WakeRequestStatus.NEEDS_HELP);
        List<WakeSuccessProjection> successes = wakeProofRepository.findSuccessHistoryByReceiverId(userId);

        return new MyStatsResponse(
                calculateSuccessRate(verified, needsHelp),
                calculateAverageGap(successes),
                calculateStreak(successes, LocalDate.now(clock))
        );
    }

    private double calculateSuccessRate(long verified, long needsHelp) {
        long total = verified + needsHelp;
        return total == 0 ? 0.0 : roundOneDecimal(verified * 100.0 / total);
    }

    private double calculateAverageGap(List<WakeSuccessProjection> successes) {
        double average = successes.stream()
                .filter(success -> success.getTargetWakeAt() != null)
                .mapToDouble(success -> Duration.between(
                        success.getTargetWakeAt(),
                        success.getVerifiedAt()
                ).toSeconds() / 60.0)
                .average()
                .orElse(0.0);
        return roundOneDecimal(average);
    }

    private int calculateStreak(List<WakeSuccessProjection> successes, LocalDate today) {
        Set<LocalDate> successDates = new HashSet<>();
        successes.forEach(success -> successDates.add(success.getVerifiedAt().toLocalDate()));

        LocalDate cursor = successDates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (successDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private double roundOneDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
