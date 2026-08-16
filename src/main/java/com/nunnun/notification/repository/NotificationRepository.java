package com.nunnun.notification.repository;

import com.nunnun.notification.entity.Notification;
import com.nunnun.notification.entity.NotificationStatus;
import com.nunnun.notification.entity.NotificationType;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            select notification from Notification notification
            join fetch notification.user
            where notification.status = :status and notification.scheduledAt <= :now
            order by notification.scheduledAt, notification.id
            """)
    List<Notification> findAllDueWithUser(
            @Param("status") NotificationStatus status,
            @Param("now") LocalDateTime now
    );

    List<Notification> findAllByUserIdAndTypeAndReferenceIdAndStatus(
            Long userId,
            NotificationType type,
            Long referenceId,
            NotificationStatus status
    );

    List<Notification> findAllByUserIdAndTypeAndStatus(
            Long userId,
            NotificationType type,
            NotificationStatus status
    );

    List<Notification> findAllByUserIdAndTypeAndTargetWakeAtAndStatus(
            Long userId,
            NotificationType type,
            LocalDateTime targetWakeAt,
            NotificationStatus status
    );

    Optional<Notification>
    findFirstByUserIdAndTypeAndStatusAndTargetWakeAtAfterOrderByTargetWakeAtAscScheduledAtAsc(
            Long userId,
            NotificationType type,
            NotificationStatus status,
            LocalDateTime now
    );

    Optional<Notification> findByUserIdAndTypeAndTargetWakeAtAndScheduledAt(
            Long userId,
            NotificationType type,
            LocalDateTime targetWakeAt,
            LocalDateTime scheduledAt
    );

    boolean existsByUserIdAndTypeAndReferenceIdAndStatus(
            Long userId,
            NotificationType type,
            Long referenceId,
            NotificationStatus status
    );

    boolean existsByUserIdAndTypeAndTargetWakeAtAndScheduledAt(
            Long userId,
            NotificationType type,
            LocalDateTime targetWakeAt,
            LocalDateTime scheduledAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select notification from Notification notification
            join fetch notification.user
            where notification.id = :id
            """)
    Optional<Notification> findByIdForUpdate(@Param("id") Long id);

    List<Notification> findAllByUserIdAndStatus(
            Long userId,
            NotificationStatus status
    );

    List<Notification> findAllByTypeAndReferenceIdIn(
            NotificationType type,
            List<Long> referenceIds
    );
}