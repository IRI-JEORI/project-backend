package com.nunnun.wake.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.user.entity.User;
import com.nunnun.wake.entity.DailyPose;
import com.nunnun.wake.entity.Pose;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.repository.DailyPoseRepository;
import com.nunnun.wake.repository.PoseRepository;
import com.nunnun.wake.repository.WakeGroupRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyPoseServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate DATE = LocalDate.of(2026, 8, 17);

    @Mock private DailyPoseRepository dailyPoses;
    @Mock private PoseRepository poses;
    @Mock private WakeGroupRepository wakeGroups;

    private WakeGroup wakeGroup;
    private DailyPoseService service;

    @BeforeEach
    void setUp() {
        User creator = User.create("creator", "creator@example.com", "password-hash");
        wakeGroup = WakeGroup.create("Wake", "POSE01", creator);
        service = new DailyPoseService(
                dailyPoses,
                poses,
                wakeGroups,
                Clock.fixed(Instant.parse("2026-08-16T15:30:00Z"), SEOUL)
        );
    }

    @Test
    void reusesExistingDailyPoseWithoutSelectingAgain() {
        Pose pose = Pose.create("HEART", "approved/heart.png", "heart pose");
        DailyPose existing = DailyPose.create(wakeGroup, pose, DATE);
        when(wakeGroups.findByIdForUpdate(1L)).thenReturn(Optional.of(wakeGroup));
        when(dailyPoses.findByWakeGroupIdAndPoseDate(1L, DATE)).thenReturn(Optional.of(existing));

        assertThat(service.getOrCreateDailyPose(1L, DATE)).isSameAs(existing);
        verify(poses, never()).findAllByActiveTrue();
        verify(dailyPoses, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createsDailyPoseFromActiveCandidates() {
        Pose first = Pose.create("HEART", "approved/heart.png", "heart pose");
        Pose second = Pose.create("V", "approved/v.png", "v pose");
        when(wakeGroups.findByIdForUpdate(1L)).thenReturn(Optional.of(wakeGroup));
        when(dailyPoses.findByWakeGroupIdAndPoseDate(1L, DATE)).thenReturn(Optional.empty());
        when(poses.findAllByActiveTrue()).thenReturn(List.of(first, second));
        when(dailyPoses.save(org.mockito.ArgumentMatchers.any(DailyPose.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DailyPose result = service.getOrCreateDailyPose(1L, DATE);

        assertThat(result.getPose()).isIn(first, second);
        assertThat(result.getWakeGroup()).isSameAs(wakeGroup);
        assertThat(result.getPoseDate()).isEqualTo(DATE);
    }

    @Test
    void failsClearlyWhenNoActivePoseExists() {
        when(wakeGroups.findByIdForUpdate(1L)).thenReturn(Optional.of(wakeGroup));
        when(dailyPoses.findByWakeGroupIdAndPoseDate(1L, DATE)).thenReturn(Optional.empty());
        when(poses.findAllByActiveTrue()).thenReturn(List.of());

        assertThatThrownBy(() -> service.getOrCreateDailyPose(1L, DATE))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACTIVE_POSE_NOT_FOUND));
        verify(dailyPoses, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void todayUsesAsiaSeoulBusinessDate() {
        when(wakeGroups.findByIdForUpdate(1L)).thenReturn(Optional.of(wakeGroup));
        Pose pose = Pose.create("HEART", "approved/heart.png", "heart pose");
        DailyPose existing = DailyPose.create(wakeGroup, pose, DATE);
        when(dailyPoses.findByWakeGroupIdAndPoseDate(1L, DATE)).thenReturn(Optional.of(existing));

        assertThat(service.getOrCreateTodayDailyPose(1L).getPoseDate()).isEqualTo(DATE);
    }
}
