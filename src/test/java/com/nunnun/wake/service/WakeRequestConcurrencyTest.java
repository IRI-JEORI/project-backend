package com.nunnun.wake.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.notification.repository.NotificationRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.entity.WakeGroupMember;
import com.nunnun.wake.repository.WakeGroupMemberRepository;
import com.nunnun.wake.repository.WakeGroupRepository;
import com.nunnun.wake.repository.WakeRequestRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WakeRequestConcurrencyTest {
    @Autowired WakeRequestService service;
    @Autowired WakeRequestRepository requests;
    @Autowired WakeGroupRepository groups;
    @Autowired WakeGroupMemberRepository members;
    @Autowired NotificationRepository notifications;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @AfterEach
    void clean() {
        notifications.deleteAllInBatch();
        requests.deleteAllInBatch();
        members.deleteAllInBatch();
        groups.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void concurrentSendersCreateExactlyOneRequestForSameReceiver() throws Exception {
        User first = user("first-race@example.com");
        User second = user("second-race@example.com");
        User third = user("third-race@example.com");
        User receiver = user("receiver-race@example.com");
        WakeGroup group = groups.saveAndFlush(WakeGroup.create("Race", "RACECODE", first));
        List<User> all = List.of(first, second, third, receiver);
        for (int index = 0; index < all.size(); index++) {
            members.saveAndFlush(WakeGroupMember.join(group, all.get(index), (short) (index + 1)));
        }

        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger cooldowns = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        for (User sender : List.of(first, second, third)) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    service.createWakeRequest(sender.getId(), group.getId(), receiver.getId());
                    successes.incrementAndGet();
                } catch (BusinessException exception) {
                    cooldowns.incrementAndGet();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(successes).hasValue(1);
        assertThat(cooldowns).hasValue(2);
        assertThat(requests.count()).isEqualTo(1);
    }

    private User user(String email) {
        return users.saveAndFlush(User.create("user", email, encoder.encode("password123!")));
    }
}
