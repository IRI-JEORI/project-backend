package com.nunnun.wake.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.wake.entity.PoseMatchResult;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.entity.WakeProof;
import com.nunnun.wake.entity.WakeRequest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WakeProofSleepStateRepositoryTest {

    @Autowired private WakeProofRepository wakeProofs;
    @Autowired private WakeRequestRepository wakeRequests;
    @Autowired private WakeGroupRepository wakeGroups;
    @Autowired private UserRepository users;

    @BeforeEach
    void setUp() {
        clearData();
    }

    @AfterEach
    void tearDown() {
        clearData();
    }

    private void clearData() {
        wakeProofs.deleteAllInBatch();
        wakeRequests.deleteAllInBatch();
        wakeGroups.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void findsOnlySuccessfulVerificationAfterTheCurrentSleepStarted() {
        User sender = saveUser("sender@example.com");
        User receiver = saveUser("receiver@example.com");
        WakeGroup group = wakeGroups.saveAndFlush(WakeGroup.create("Wake", "SLP001", sender));
        LocalDateTime sleepStartedAt = LocalDateTime.of(2026, 8, 19, 23, 40);

        saveSuccess(group, sender, receiver, sleepStartedAt.minusMinutes(1), "old.jpg");
        assertThat(wakeProofs.existsSuccessfulVerificationAfter(receiver.getId(), sleepStartedAt))
                .isFalse();

        saveSuccess(group, sender, receiver, sleepStartedAt.plusHours(8), "new.jpg");
        assertThat(wakeProofs.existsSuccessfulVerificationAfter(receiver.getId(), sleepStartedAt))
                .isTrue();
    }

    @Test
    void doesNotTreatNeedsHelpAsAWakeSuccess() {
        User sender = saveUser("needs-help-sender@example.com");
        User receiver = saveUser("needs-help-receiver@example.com");
        WakeGroup group = wakeGroups.saveAndFlush(WakeGroup.create("Needs Help", "SLP003", sender));
        LocalDateTime sleepStartedAt = LocalDateTime.of(2026, 8, 19, 23, 40);
        WakeRequest request = wakeRequests.saveAndFlush(
                WakeRequest.send(group, sender, receiver, sleepStartedAt.plusHours(8))
        );
        request.recordProofResult(false);
        request.recordProofResult(false);
        wakeRequests.saveAndFlush(request);
        wakeProofs.saveAndFlush(WakeProof.record(
                request,
                "needs-help.jpg",
                20,
                PoseMatchResult.FAIL,
                sleepStartedAt.plusHours(8).plusMinutes(1)
        ));

        assertThat(wakeProofs.existsSuccessfulVerificationAfter(receiver.getId(), sleepStartedAt))
                .isFalse();
    }

    @Test
    void treatsSelfVerifySuccessAsTheReceiversWakeSuccess() {
        User user = saveUser("self@example.com");
        WakeGroup group = wakeGroups.saveAndFlush(WakeGroup.create("Self", "SLP002", user));
        LocalDateTime sleepStartedAt = LocalDateTime.of(2026, 8, 19, 23, 40);

        saveSuccess(group, user, user, sleepStartedAt.plusMinutes(1), "self.jpg");

        assertThat(wakeProofs.existsSuccessfulVerificationAfter(user.getId(), sleepStartedAt))
                .isTrue();
    }

    private void saveSuccess(
            WakeGroup group,
            User sender,
            User receiver,
            LocalDateTime verifiedAt,
            String imageKey
    ) {
        WakeRequest request = wakeRequests.saveAndFlush(
                WakeRequest.send(group, sender, receiver, verifiedAt.minusSeconds(1))
        );
        request.recordProofResult(true);
        wakeRequests.saveAndFlush(request);
        wakeProofs.saveAndFlush(WakeProof.record(
                request,
                imageKey,
                90,
                PoseMatchResult.SUCCESS,
                verifiedAt
        ));
    }

    private User saveUser(String email) {
        return users.saveAndFlush(User.create("nunnun", email, "password-hash"));
    }
}
