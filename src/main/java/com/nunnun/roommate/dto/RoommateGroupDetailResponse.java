package com.nunnun.roommate.dto;

import com.nunnun.roommate.entity.RoommateGroup;
import com.nunnun.roommate.entity.RoommateGroupMember;
import com.nunnun.roommate.entity.RoommateGroupStatus;
import com.nunnun.routine.entity.DailyRoutine;
import com.nunnun.schedule.dto.FixedScheduleResponse;
import com.nunnun.sleep.entity.SleepSession;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public record RoommateGroupDetailResponse(
        Long id,
        String name,
        RoommateGroupStatus status,
        List<MemberResponse> members
) {
    public static RoommateGroupDetailResponse from(
            RoommateGroup group,
            List<RoommateGroupMember> members,
            Map<Long, DailyRoutine> routinesByUserId,
            Map<Long, List<FixedScheduleResponse>> schedulesByUserId,
            Map<Long, SleepResponse> sleepsByUserId
    ) {
        return new RoommateGroupDetailResponse(
                group.getId(),
                group.getName(),
                group.getStatus(),
                members.stream()
                        .map(member -> MemberResponse.from(
                                member,
                                routinesByUserId.get(member.getUser().getId()),
                                schedulesByUserId.getOrDefault(member.getUser().getId(), List.of()),
                                sleepsByUserId.get(member.getUser().getId())
                        ))
                        .toList()
        );
    }

    public record MemberResponse(
            Long userId,
            String nickname,
            Short slotNo,
            TodayRoutineResponse todayRoutine,
            List<FixedScheduleResponse> todaySchedules,
            SleepResponse sleep
    ) {
        private static MemberResponse from(
                RoommateGroupMember member,
                DailyRoutine routine,
                List<FixedScheduleResponse> schedules,
                SleepResponse sleep
        ) {
            return new MemberResponse(
                    member.getUser().getId(),
                    member.getUser().getNickname(),
                    member.getSlotNo(),
                    TodayRoutineResponse.from(routine),
                    schedules,
                    sleep
            );
        }
    }

    public record TodayRoutineResponse(
            LocalTime targetBedTime,
            LocalTime targetWakeTime,
            LocalTime estimatedReturnTime
    ) {
        private static TodayRoutineResponse from(DailyRoutine routine) {
            if (routine == null) {
                return null;
            }
            return new TodayRoutineResponse(
                    routine.getTargetBedTime(),
                    routine.getTargetWakeTime(),
                    routine.getEstimatedReturnTime()
            );
        }
    }

    public record SleepResponse(LocalDateTime startedAt, long elapsedMinutes) {
        public static SleepResponse from(SleepSession session, long elapsedMinutes) {
            return new SleepResponse(session.getStartedAt(), elapsedMinutes);
        }
    }
}
