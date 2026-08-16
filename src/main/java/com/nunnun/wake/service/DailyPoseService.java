package com.nunnun.wake.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.wake.entity.DailyPose;
import com.nunnun.wake.entity.Pose;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.repository.DailyPoseRepository;
import com.nunnun.wake.repository.PoseRepository;
import com.nunnun.wake.repository.WakeGroupRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyPoseService {

    private final DailyPoseRepository dailyPoseRepository;
    private final PoseRepository poseRepository;
    private final WakeGroupRepository wakeGroupRepository;
    private final Clock clock;

    public DailyPoseService(
            DailyPoseRepository dailyPoseRepository,
            PoseRepository poseRepository,
            WakeGroupRepository wakeGroupRepository,
            Clock clock
    ) {
        this.dailyPoseRepository = dailyPoseRepository;
        this.poseRepository = poseRepository;
        this.wakeGroupRepository = wakeGroupRepository;
        this.clock = clock;
    }

    @Transactional
    public DailyPose getOrCreateTodayDailyPose(Long wakeGroupId) {
        return getOrCreateDailyPose(wakeGroupId, LocalDate.now(clock));
    }

    @Transactional
    public DailyPose getOrCreateDailyPose(Long wakeGroupId, LocalDate poseDate) {
        WakeGroup wakeGroup = wakeGroupRepository.findByIdForUpdate(wakeGroupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_GROUP_NOT_FOUND));

        return dailyPoseRepository.findByWakeGroupIdAndPoseDate(wakeGroupId, poseDate)
                .orElseGet(() -> createDailyPose(wakeGroup, poseDate));
    }

    @Transactional(readOnly = true)
    public DailyPose getDailyPose(Long wakeGroupId, LocalDate poseDate) {
        return dailyPoseRepository.findByWakeGroupIdAndPoseDate(wakeGroupId, poseDate)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTIVE_POSE_NOT_FOUND));
    }

    private DailyPose createDailyPose(WakeGroup wakeGroup, LocalDate poseDate) {
        List<Pose> activePoses = poseRepository.findAllByActiveTrue();
        if (activePoses.isEmpty()) {
            throw new BusinessException(ErrorCode.ACTIVE_POSE_NOT_FOUND);
        }
        Pose selectedPose = activePoses.get(ThreadLocalRandom.current().nextInt(activePoses.size()));
        return dailyPoseRepository.save(DailyPose.create(wakeGroup, selectedPose, poseDate));
    }
}
