package com.nunnun.wake.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.roommate.entity.RoommateGroup;
import com.nunnun.roommate.entity.RoommateGroupMember;
import com.nunnun.roommate.repository.RoommateBehaviorManualRepository;
import com.nunnun.roommate.repository.RoommateComplaintRepository;
import com.nunnun.roommate.repository.RoommateGroupMemberRepository;
import com.nunnun.roommate.repository.RoommateGroupRepository;
import com.nunnun.roommate.service.RoommateGroupService;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.entity.WakeGroupMember;
import com.nunnun.wake.repository.WakeGroupMemberRepository;
import com.nunnun.wake.repository.WakeGroupRepository;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class GroupLifecycleConcurrencyTest {
    @Autowired private WakeGroupService wakeGroupService;
    @Autowired private RoommateGroupService roommateGroupService;
    @Autowired private WakeGroupRepository wakeGroups;
    @Autowired private WakeGroupMemberRepository wakeMembers;
    @Autowired private RoommateGroupRepository roommateGroups;
    @Autowired private RoommateGroupMemberRepository roommateMembers;
    @Autowired private RoommateComplaintRepository complaints;
    @Autowired private RoommateBehaviorManualRepository manuals;
    @Autowired private UserRepository users;

    @BeforeEach
    @AfterEach
    void clean() {
        manuals.deleteAllInBatch();
        complaints.deleteAllInBatch();
        roommateMembers.deleteAllInBatch();
        roommateGroups.deleteAllInBatch();
        wakeMembers.deleteAllInBatch();
        wakeGroups.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void simultaneousWakeLeavesSerializeAndDeleteTheNowEmptyGroup() throws Exception {
        User a = user("wake-concurrent-a@example.com");
        User b = user("wake-concurrent-b@example.com");
        WakeGroup group = wakeGroups.saveAndFlush(WakeGroup.create("wake", "CONCURRENTW", a));
        wakeMembers.saveAndFlush(WakeGroupMember.join(group, a, (short) 1));
        wakeMembers.saveAndFlush(WakeGroupMember.join(group, b, (short) 2));

        List<Throwable> results = runTogether(
                () -> wakeGroupService.leaveWakeGroup(a.getId(), group.getId()),
                () -> wakeGroupService.leaveWakeGroup(b.getId(), group.getId())
        );

        assertThat(results).containsOnlyNulls();
        assertThat(wakeMembers.findAllByWakeGroupId(group.getId())).isEmpty();
        assertThat(wakeGroups.findById(group.getId())).isEmpty();
    }

    @Test
    void simultaneousRoommateLeavesEndWithControlledResultAndNoPartialRelationship() throws Exception {
        User a = user("room-concurrent-a@example.com");
        User b = user("room-concurrent-b@example.com");
        RoommateGroup group = roommateGroups.saveAndFlush(RoommateGroup.create("room", "CONCURRENTR", a));
        roommateMembers.saveAndFlush(RoommateGroupMember.join(group, a, (short) 1));
        roommateMembers.saveAndFlush(RoommateGroupMember.join(group, b, (short) 2));
        group.activate();
        roommateGroups.saveAndFlush(group);

        List<Throwable> results = runTogether(
                () -> roommateGroupService.leave(a.getId(), group.getId()),
                () -> roommateGroupService.leave(b.getId(), group.getId())
        );

        assertThat(results).allMatch(result -> result == null || result instanceof BusinessException);
        assertThat(results).anyMatch(result -> result == null);
        assertThat(roommateMembers.findAllByRoommateGroupId(group.getId())).isEmpty();
        assertThat(roommateGroups.findById(group.getId())).isEmpty();
    }

    private List<Throwable> runTogether(ThrowingAction first, ThrowingAction second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Throwable> firstResult = executor.submit(() -> run(ready, start, first));
            Future<Throwable> secondResult = executor.submit(() -> run(ready, start, second));
            ready.await();
            start.countDown();
            return Arrays.asList(firstResult.get(), secondResult.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private Throwable run(CountDownLatch ready, CountDownLatch start, ThrowingAction action) {
        ready.countDown();
        try {
            start.await();
            action.run();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private User user(String email) {
        return users.saveAndFlush(User.create("user", email, "password-hash"));
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
