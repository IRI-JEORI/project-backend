package com.nunnun.wake.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.wake.dto.ShareWakeProofResponse;
import com.nunnun.wake.entity.PoseMatchResult;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.entity.WakeProof;
import com.nunnun.wake.entity.WakeProofShare;
import com.nunnun.wake.entity.WakeRequest;
import com.nunnun.wake.entity.WakeRequestStatus;
import com.nunnun.wake.repository.WakeGroupMemberRepository;
import com.nunnun.wake.repository.WakeGroupRepository;
import com.nunnun.wake.repository.WakeProofRepository;
import com.nunnun.wake.repository.WakeProofShareRepository;
import com.nunnun.wake.repository.WakeRequestRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WakeProofShareService {

    private final WakeRequestRepository requests;
    private final WakeProofRepository proofs;
    private final WakeGroupRepository groups;
    private final WakeGroupMemberRepository members;
    private final WakeProofShareRepository shares;
    private final Clock clock;

    public WakeProofShareService(
            WakeRequestRepository requests,
            WakeProofRepository proofs,
            WakeGroupRepository groups,
            WakeGroupMemberRepository members,
            WakeProofShareRepository shares,
            Clock clock
    ) {
        this.requests = requests;
        this.proofs = proofs;
        this.groups = groups;
        this.members = members;
        this.shares = shares;
        this.clock = clock;
    }

    @Transactional
    public ShareWakeProofResponse share(Long userId, Long requestId, List<Long> requestedGroupIds) {
        WakeRequest request = requests.findDetailById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_REQUEST_NOT_FOUND));
        if (!request.getReceiver().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.WAKE_REQUEST_ACCESS_DENIED);
        }
        WakeProof proof = proofs.findByWakeRequestId(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_PROOF_SHARE_NOT_ALLOWED));
        if (request.getStatus() != WakeRequestStatus.VERIFIED
                || proof.getPoseMatchResult() != PoseMatchResult.SUCCESS) {
            throw new BusinessException(ErrorCode.WAKE_PROOF_SHARE_NOT_ALLOWED);
        }

        LinkedHashSet<Long> groupIds = new LinkedHashSet<>(requestedGroupIds);
        Long originalGroupId = request.getWakeGroup().getId();
        if (!groupIds.contains(originalGroupId)) {
            throw new BusinessException(ErrorCode.WAKE_PROOF_ORIGINAL_GROUP_REQUIRED);
        }

        List<WakeGroup> selectedGroups = groupIds.stream().map(groupId -> {
            WakeGroup group = groups.findById(groupId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_GROUP_NOT_FOUND));
            if (!members.existsByWakeGroupIdAndUserId(groupId, userId)) {
                throw new BusinessException(ErrorCode.WAKE_GROUP_ACCESS_DENIED);
            }
            return group;
        }).toList();

        shares.deleteAllByWakeProofId(proof.getId());
        shares.flush();
        LocalDateTime sharedAt = LocalDateTime.now(clock);
        shares.saveAll(selectedGroups.stream()
                .map(group -> WakeProofShare.share(proof, group, sharedAt))
                .toList());
        return new ShareWakeProofResponse(List.copyOf(groupIds));
    }
}
