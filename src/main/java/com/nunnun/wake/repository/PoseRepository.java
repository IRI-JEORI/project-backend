package com.nunnun.wake.repository;

import com.nunnun.wake.entity.Pose;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PoseRepository extends JpaRepository<Pose, Long> {

    List<Pose> findAllByActiveTrue();
}
