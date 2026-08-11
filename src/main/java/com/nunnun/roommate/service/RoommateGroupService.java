package com.nunnun.roommate.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.roommate.dto.RoommateGroupDetailResponse;
import com.nunnun.roommate.entity.RoommateGroup;
import com.nunnun.roommate.entity.RoommateGroupMember;
import com.nunnun.roommate.entity.RoommateGroupStatus;
import com.nunnun.roommate.repository.RoommateGroupMemberRepository;
import com.nunnun.roommate.repository.RoommateGroupRepository;
import com.nunnun.routine.entity.DailyRoutine;
import com.nunnun.routine.repository.DailyRoutineRepository;
import com.nunnun.schedule.dto.FixedScheduleResponse;
import com.nunnun.schedule.entity.FixedSchedule;
import com.nunnun.schedule.repository.FixedScheduleRepository;
import com.nunnun.sleep.entity.SleepSession;
import com.nunnun.sleep.repository.SleepSessionRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.wake.service.InviteCodeGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoommateGroupService {
    private final RoommateGroupRepository groups;
    private final RoommateGroupMemberRepository members;
    private final UserRepository users;
    private final InviteCodeGenerator codes;
    private final DailyRoutineRepository routines;
    private final FixedScheduleRepository schedules;
    private final SleepSessionRepository sleepSessions;
    private final Clock clock;

    public RoommateGroupService(RoommateGroupRepository groups, RoommateGroupMemberRepository members,
                                UserRepository users, InviteCodeGenerator codes,
                                DailyRoutineRepository routines, FixedScheduleRepository schedules,
                                SleepSessionRepository sleepSessions, Clock clock) {
        this.groups = groups;
        this.members = members;
        this.users = users;
        this.codes = codes;
        this.routines = routines;
        this.schedules = schedules;
        this.sleepSessions = sleepSessions;
        this.clock = clock;
    }

    @Transactional
    public RoommateGroup create(Long userId, String name) {
        User user = user(userId);
        ensureFree(userId);
        RoommateGroup group = groups.save(RoommateGroup.create(name, code(), user));
        members.save(RoommateGroupMember.join(group, user, (short) 1));
        return group;
    }

    @Transactional
    public RoommateGroup join(Long userId, String inviteCode) {
        User user = user(userId);
        ensureFree(userId);
        RoommateGroup group = groups.findByInviteCodeForUpdate(inviteCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOMMATE_GROUP_NOT_FOUND));
        if (group.getStatus() != RoommateGroupStatus.WAITING || members.countByRoommateGroupId(group.getId()) != 1) {
            throw new BusinessException(ErrorCode.ROOMMATE_GROUP_FULL);
        }
        short slot = members.findAllByRoommateGroupId(group.getId()).stream()
                .anyMatch(member -> member.getSlotNo() == 1) ? (short) 2 : (short) 1;
        members.save(RoommateGroupMember.join(group, user, slot));
        group.activate();
        return group;
    }

    @Transactional(readOnly = true)
    public RoommateGroupDetailResponse getDetail(Long userId, Long groupId) {
        RoommateGroup group = groups.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOMMATE_GROUP_NOT_FOUND));
        List<RoommateGroupMember> groupMembers = members.findAllWithUserByRoommateGroupId(groupId);
        boolean isMember = groupMembers.stream().anyMatch(member -> member.getUser().getId().equals(userId));
        if (!isMember) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> memberUserIds = groupMembers.stream().map(member -> member.getUser().getId()).toList();
        Map<Long, DailyRoutine> routinesByUserId = routines.findAllByUserIdInAndRoutineDate(memberUserIds, today)
                .stream().collect(Collectors.toMap(routine -> routine.getUser().getId(), Function.identity()));
        Map<Long, List<FixedScheduleResponse>> schedulesByUserId = schedules
                .findAllByUserIdInAndDayOfWeekOrderByUserIdAscStartTimeAscEndTimeAsc(memberUserIds, today.getDayOfWeek())
                .stream().collect(Collectors.groupingBy(
                        schedule -> schedule.getUser().getId(),
                        Collectors.mapping(FixedScheduleResponse::from, Collectors.toList())
                ));
        Map<Long, RoommateGroupDetailResponse.SleepResponse> sleepsByUserId = latestSleeps(
                sleepSessions.findAllByUserIdInAndSleepDateOrderByUserIdAscStartedAtDesc(memberUserIds, today), now
        );
        return RoommateGroupDetailResponse.from(group, groupMembers, routinesByUserId, schedulesByUserId, sleepsByUserId);
    }

    @Transactional
    public void leave(Long userId, Long groupId) {
        RoommateGroupMember member = members.findByRoommateGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOMMATE_GROUP_MEMBER_NOT_FOUND));
        RoommateGroup group = member.getRoommateGroup();
        members.delete(member);
        if (members.countByRoommateGroupId(groupId) == 1) {
            group.waitForRoommate();
        } else if (members.countByRoommateGroupId(groupId) == 0) {
            groups.delete(group);
        }
    }

    @Transactional(readOnly = true)
    public String invite(Long userId, Long groupId) {
        if (!members.existsByRoommateGroupIdAndUserId(groupId, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return groups.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOMMATE_GROUP_NOT_FOUND))
                .getInviteCode();
    }

    private User user(Long id) {
        return users.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void ensureFree(Long id) {
        if (members.existsByUserId(id)) {
            throw new BusinessException(ErrorCode.ROOMMATE_GROUP_ALREADY_EXISTS);
        }
    }

    private String code() {
        for (int i = 0; i < 5; i++) {
            String code = codes.generate();
            if (!groups.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.INVITE_CODE_GENERATION_FAILED);
    }

    private Map<Long, RoommateGroupDetailResponse.SleepResponse> latestSleeps(
            Collection<SleepSession> sessions, LocalDateTime now
    ) {
        return sessions.stream().collect(Collectors.toMap(
                session -> session.getUser().getId(),
                session -> RoommateGroupDetailResponse.SleepResponse.from(
                        session, Duration.between(session.getStartedAt(), now).toMinutes()
                ),
                (first, ignored) -> first
        ));
    }
}
