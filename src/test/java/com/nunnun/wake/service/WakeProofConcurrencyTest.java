package com.nunnun.wake.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.wake.entity.PoseMatchResult;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.entity.WakeProof;
import com.nunnun.wake.entity.WakeRequest;
import com.nunnun.wake.entity.WakeRequestStatus;
import com.nunnun.wake.repository.WakeGroupRepository;
import com.nunnun.wake.repository.WakeProofRepository;
import com.nunnun.wake.repository.WakeRequestRepository;
import java.time.LocalDateTime;
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
class WakeProofConcurrencyTest {

    @Autowired WakeProofPersistenceService service;
    @Autowired WakeProofRepository proofs;
    @Autowired WakeRequestRepository requests;
    @Autowired WakeGroupRepository groups;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @AfterEach
    void clean() {
        proofs.deleteAllInBatch();
        requests.deleteAllInBatch();
        groups.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void concurrentSuccessAndFailureNeverOverwriteVerifiedOrCreateSecondProof() throws Exception {
        User user = users.saveAndFlush(User.create(
                "proof-race", "proof-race@example.com", encoder.encode("password123!")));
        WakeGroup group = groups.saveAndFlush(WakeGroup.create("Race", "PROOF1", user));
        WakeRequest request = requests.saveAndFlush(WakeRequest.send(
                group, user, user, LocalDateTime.of(2026, 8, 16, 8, 0)));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        for (int score : List.of(82, 40)) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    service.applyResult(request.getId(), user.getId(), "wake-proofs/race-" + score + ".jpg", score);
                    completed.incrementAndGet();
                } catch (BusinessException ignored) {
                    // A stale result is expected when SUCCESS commits first.
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        WakeRequest storedRequest = requests.findById(request.getId()).orElseThrow();
        WakeProof storedProof = proofs.findByWakeRequestId(request.getId()).orElseThrow();
        assertThat(completed.get()).isBetween(1, 2);
        assertThat(storedRequest.getAttemptCount()).isBetween((short) 1, (short) 2);
        assertThat(storedRequest.getStatus()).isEqualTo(WakeRequestStatus.VERIFIED);
        assertThat(storedProof.getPoseMatchResult()).isEqualTo(PoseMatchResult.SUCCESS);
        assertThat(proofs.count()).isOne();
    }
}
