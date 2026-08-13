package com.nunnun.wake.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.notification.entity.Notification;
import com.nunnun.notification.entity.NotificationType;
import com.nunnun.notification.repository.NotificationRepository;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.entity.WakeGroupMember;
import com.nunnun.wake.entity.WakeProof;
import com.nunnun.wake.entity.WakeRequest;
import com.nunnun.wake.repository.WakeGroupMemberRepository;
import com.nunnun.wake.repository.WakeGroupRepository;
import com.nunnun.wake.repository.WakeProofRepository;
import com.nunnun.wake.repository.WakeRequestRepository;
import com.nunnun.wake.storage.WakeProofStorage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class WakeGroupLifecycleService {
    private static final Logger log = LoggerFactory.getLogger(WakeGroupLifecycleService.class);

    private final WakeGroupRepository groups;
    private final WakeGroupMemberRepository members;
    private final WakeRequestRepository requests;
    private final WakeProofRepository proofs;
    private final NotificationRepository notifications;
    private final WakeProofStorage storage;

    public WakeGroupLifecycleService(
            WakeGroupRepository groups,
            WakeGroupMemberRepository members,
            WakeRequestRepository requests,
            WakeProofRepository proofs,
            NotificationRepository notifications,
            WakeProofStorage storage
    ) {
        this.groups = groups;
        this.members = members;
        this.requests = requests;
        this.proofs = proofs;
        this.notifications = notifications;
        this.storage = storage;
    }

    @Transactional
    public void leave(Long userId, Long groupId) {
        removeMembership(userId, groupId, true);
    }

    @Transactional
    public void withdraw(Long userId, Long groupId) {
        removeMembership(userId, groupId, false);
    }

    private void removeMembership(Long userId, Long groupId, boolean strict) {
        WakeGroup group = groups.findByIdForUpdate(groupId).orElse(null);
        if (group == null) {
            if (strict) {
                throw new BusinessException(ErrorCode.WAKE_GROUP_NOT_FOUND);
            }
            return;
        }
        WakeGroupMember member = members.findByWakeGroupIdAndUserId(groupId, userId).orElse(null);
        if (member == null) {
            if (strict) {
                throw new BusinessException(ErrorCode.WAKE_GROUP_MEMBER_NOT_FOUND);
            }
            return;
        }

        members.delete(member);
        members.flush();
        if (!members.findAllByWakeGroupId(groupId).isEmpty()) {
            return;
        }
        deleteEmptyGroup(group);
    }

    private void deleteEmptyGroup(WakeGroup group) {
        Long groupId = group.getId();
        List<WakeRequest> groupRequests = requests.findAllByWakeGroupId(groupId);
        List<Long> requestIds = groupRequests.stream().map(WakeRequest::getId).toList();
        List<WakeProof> groupProofs = proofs.findAllByWakeRequestWakeGroupId(groupId);
        List<String> objectKeys = groupProofs.stream().map(WakeProof::getImageObjectKey).toList();

        if (!requestIds.isEmpty()) {
            List<Notification> wakeNotifications = notifications.findAllByTypeAndReferenceIdIn(
                    NotificationType.WAKE_REQUEST, requestIds
            );
            notifications.deleteAll(wakeNotifications);
            notifications.flush();
        }
        proofs.deleteAll(groupProofs);
        proofs.flush();
        requests.deleteAll(groupRequests);
        requests.flush();
        groups.delete(group);
        groups.flush();
        deleteProofObjectsAfterCommit(groupId, objectKeys);
    }

    private void deleteProofObjectsAfterCommit(Long groupId, List<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String objectKey : objectKeys) {
                    try {
                        storage.delete(objectKey);
                    } catch (RuntimeException exception) {
                        log.error("Wake group proof deletion failed after group cleanup; orphan sweep will retry. groupId={}",
                                groupId);
                    }
                }
            }
        });
    }
}
