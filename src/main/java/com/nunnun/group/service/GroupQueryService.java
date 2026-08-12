package com.nunnun.group.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.group.dto.GroupListResponse;
import com.nunnun.group.dto.GroupSummaryResponse;
import com.nunnun.group.dto.GroupType;
import com.nunnun.roommate.entity.RoommateGroup;
import com.nunnun.roommate.entity.RoommateGroupStatus;
import com.nunnun.roommate.repository.RoommateGroupMemberRepository;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.repository.WakeGroupMemberRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupQueryService {

    private static final Comparator<GroupEntry> GROUP_ORDER = Comparator
            .comparing(GroupEntry::createdAt, Comparator.reverseOrder())
            .thenComparing(GroupEntry::type)
            .thenComparing(GroupEntry::id);

    private final UserRepository users;
    private final WakeGroupMemberRepository wakeGroupMembers;
    private final RoommateGroupMemberRepository roommateGroupMembers;

    public GroupQueryService(
            UserRepository users,
            WakeGroupMemberRepository wakeGroupMembers,
            RoommateGroupMemberRepository roommateGroupMembers
    ) {
        this.users = users;
        this.wakeGroupMembers = wakeGroupMembers;
        this.roommateGroupMembers = roommateGroupMembers;
    }

    @Transactional(readOnly = true)
    public GroupListResponse getMyGroups(Long userId) {
        users.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<GroupEntry> entries = new ArrayList<>();
        wakeGroupMembers.findAllWakeGroupsByUserId(userId).stream()
                .map(GroupEntry::from)
                .forEach(entries::add);
        roommateGroupMembers.findAllRoommateGroupsByUserId(userId).stream()
                .map(GroupEntry::from)
                .forEach(entries::add);

        List<GroupSummaryResponse> responses = entries.stream()
                .sorted(GROUP_ORDER)
                .map(GroupEntry::toResponse)
                .toList();
        return new GroupListResponse(responses);
    }

    private record GroupEntry(
            Long id,
            GroupType type,
            String name,
            RoommateGroupStatus status,
            LocalDateTime createdAt
    ) {
        private static GroupEntry from(WakeGroup group) {
            return new GroupEntry(group.getId(), GroupType.WAKE, group.getName(), null, group.getCreatedAt());
        }

        private static GroupEntry from(RoommateGroup group) {
            return new GroupEntry(
                    group.getId(), GroupType.ROOMMATE, group.getName(), group.getStatus(), group.getCreatedAt()
            );
        }

        private GroupSummaryResponse toResponse() {
            return new GroupSummaryResponse(id, type, name, status);
        }
    }
}
