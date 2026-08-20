package com.nunnun.wake.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.entity.WakeGroupMember;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WakeGroupMemberRepositoryTest {

    @Autowired private WakeGroupRepository wakeGroupRepository;
    @Autowired private WakeGroupMemberRepository wakeGroupMemberRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        clearData();
    }

    @AfterEach
    void tearDown() {
        clearData();
    }

    @Test
    void enforcesGroupUserAndGroupSlotUniqueConstraints() {
        User creator = saveUser("creator@example.com");
        User anotherUser = saveUser("another@example.com");
        WakeGroup group = saveGroup(creator, "CODE01");
        WakeGroup anotherGroup = saveGroup(anotherUser, "CODE03");
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(group, creator, (short) 1));

        assertThatThrownBy(() -> wakeGroupMemberRepository.saveAndFlush(
                WakeGroupMember.join(group, creator, (short) 2)
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> wakeGroupMemberRepository.saveAndFlush(
                WakeGroupMember.join(group, anotherUser, (short) 1)
        )).isInstanceOf(DataIntegrityViolationException.class);
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(anotherGroup, creator, (short) 1));
        assertThat(wakeGroupMemberRepository.findAllByUserId(creator.getId())).hasSize(2);
    }

    @Test
    void enforcesSlotNumberCheckConstraint() {
        User creator = saveUser("creator@example.com");
        WakeGroup group = saveGroup(creator, "CODE02");

        assertThatThrownBy(() -> wakeGroupMemberRepository.saveAndFlush(
                WakeGroupMember.join(group, creator, (short) 0)
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> wakeGroupMemberRepository.saveAndFlush(
                WakeGroupMember.join(group, creator, (short) 9)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void clearData() {
        wakeGroupMemberRepository.deleteAllInBatch();
        wakeGroupRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(User.create("nunnun", email, "password-hash"));
    }

    private WakeGroup saveGroup(User creator, String inviteCode) {
        return wakeGroupRepository.saveAndFlush(WakeGroup.create("Wake Group", inviteCode, creator));
    }
}
