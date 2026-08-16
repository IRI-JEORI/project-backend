package com.nunnun.wake.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.entity.WakeGroupMember;
import com.nunnun.wake.repository.WakeGroupMemberRepository;
import com.nunnun.wake.repository.WakeGroupRepository;
import java.util.Arrays;
import java.util.List;
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
class WakeGroupJoinConcurrencyTest {

    @Autowired private WakeGroupService wakeGroupService;
    @Autowired private WakeGroupRepository groups;
    @Autowired private WakeGroupMemberRepository members;
    @Autowired private UserRepository users;

    @BeforeEach
    @AfterEach
    void clean() {
        members.deleteAllInBatch();
        groups.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void concurrentJoinAtThreeOfFourAcceptsExactlyOneUser() throws Exception {
        User creator = user("join-owner@example.com");
        User second = user("join-second@example.com");
        User third = user("join-third@example.com");
        User firstCandidate = user("join-candidate-a@example.com");
        User secondCandidate = user("join-candidate-b@example.com");
        WakeGroup group = groups.saveAndFlush(WakeGroup.create("Wake", "CONJ01", creator));
        members.saveAndFlush(WakeGroupMember.join(group, creator, (short) 1));
        members.saveAndFlush(WakeGroupMember.join(group, second, (short) 2));
        members.saveAndFlush(WakeGroupMember.join(group, third, (short) 3));

        List<Throwable> results = runTogether(
                () -> wakeGroupService.joinWakeGroup(firstCandidate.getId(), group.getInviteCode()),
                () -> wakeGroupService.joinWakeGroup(secondCandidate.getId(), group.getInviteCode())
        );

        assertThat(results).filteredOn(result -> result == null).hasSize(1);
        assertThat(results).filteredOn(BusinessException.class::isInstance)
                .singleElement()
                .satisfies(result -> assertThat(((BusinessException) result).getErrorCode())
                        .isEqualTo(ErrorCode.WAKE_GROUP_FULL));
        assertThat(members.findAllByWakeGroupId(group.getId()))
                .hasSize(4)
                .extracting(WakeGroupMember::getSlotNo)
                .doesNotHaveDuplicates()
                .allMatch(slot -> slot >= 1 && slot <= 4);
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
