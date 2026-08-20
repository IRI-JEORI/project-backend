package com.nunnun.wake.repository;

import com.nunnun.wake.entity.WakeProofShare;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WakeProofShareRepository extends JpaRepository<WakeProofShare, Long> {

    @EntityGraph(attributePaths = {"wakeProof", "wakeProof.wakeRequest", "wakeProof.wakeRequest.receiver"})
    List<WakeProofShare> findAllByWakeGroupId(Long wakeGroupId);

    void deleteAllByWakeProofId(Long wakeProofId);

    List<WakeProofShare> findAllByWakeProofId(Long wakeProofId);
}
