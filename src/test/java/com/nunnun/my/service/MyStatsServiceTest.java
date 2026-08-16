package com.nunnun.my.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nunnun.my.dto.MyStatsResponse;
import com.nunnun.wake.entity.WakeRequestStatus;
import com.nunnun.wake.repository.WakeProofRepository;
import com.nunnun.wake.repository.WakeRequestRepository;
import com.nunnun.wake.repository.WakeSuccessProjection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MyStatsServiceTest {

    private static final Long USER_ID = 1L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-12T03:00:00Z"), ZoneId.of("Asia/Seoul")
    );

    @Mock private WakeRequestRepository wakeRequestRepository;
    @Mock private WakeProofRepository wakeProofRepository;
    private MyStatsService service;

    @BeforeEach
    void setUp() {
        service = new MyStatsService(wakeRequestRepository, wakeProofRepository, CLOCK);
    }

    @Test
    void returnsZerosWithoutFinalRequestsOrSuccessProofs() {
        when(wakeProofRepository.findSuccessHistoryByReceiverId(USER_ID)).thenReturn(List.of());

        MyStatsResponse result = service.getStats(USER_ID);

        assertThat(result.successRate()).isEqualTo(0.0);
        assertThat(result.averageGapMinutes()).isEqualTo(0.0);
        assertThat(result.streakDays()).isZero();
    }

    @Test
    void calculatesFinalRequestSuccessRateAndRoundsToOneDecimal() {
        when(wakeRequestRepository.countByReceiverIdAndStatus(USER_ID, WakeRequestStatus.VERIFIED))
                .thenReturn(2L);
        when(wakeRequestRepository.countByReceiverIdAndStatus(USER_ID, WakeRequestStatus.NEEDS_HELP))
                .thenReturn(1L);
        when(wakeProofRepository.findSuccessHistoryByReceiverId(USER_ID)).thenReturn(List.of());

        assertThat(service.getStats(USER_ID).successRate()).isEqualTo(66.7);
    }

    @Test
    void averagesOnlySuccessesWithHistoricalTargetSnapshots() {
        when(wakeProofRepository.findSuccessHistoryByReceiverId(USER_ID)).thenReturn(List.of(
                success("2026-08-10T07:20:00", "2026-08-10T07:30:00"),
                success("2026-08-11T07:45:00", "2026-08-11T07:30:00"),
                success("2026-08-12T07:00:00", null)
        ));

        assertThat(service.getStats(USER_ID).averageGapMinutes()).isEqualTo(2.5);
    }

    @Test
    void countsDistinctConsecutiveSuccessDatesFromToday() {
        when(wakeProofRepository.findSuccessHistoryByReceiverId(USER_ID)).thenReturn(List.of(
                success("2026-08-12T07:00:00", null),
                success("2026-08-12T08:00:00", null),
                success("2026-08-11T07:00:00", null),
                success("2026-08-10T07:00:00", null),
                success("2026-08-08T07:00:00", null)
        ));

        assertThat(service.getStats(USER_ID).streakDays()).isEqualTo(3);
    }

    @Test
    void startsStreakFromYesterdayWhenTodayHasNoSuccess() {
        when(wakeProofRepository.findSuccessHistoryByReceiverId(USER_ID)).thenReturn(List.of(
                success("2026-08-11T07:00:00", null),
                success("2026-08-10T07:00:00", null),
                success("2026-08-09T07:00:00", null)
        ));

        assertThat(service.getStats(USER_ID).streakDays()).isEqualTo(3);
    }

    private WakeSuccessProjection success(String verifiedAt, String targetWakeAt) {
        LocalDateTime verified = LocalDateTime.parse(verifiedAt);
        LocalDateTime target = targetWakeAt == null ? null : LocalDateTime.parse(targetWakeAt);
        return new WakeSuccessProjection() {
            @Override public LocalDateTime getVerifiedAt() { return verified; }
            @Override public LocalDateTime getTargetWakeAt() { return target; }
        };
    }
}
