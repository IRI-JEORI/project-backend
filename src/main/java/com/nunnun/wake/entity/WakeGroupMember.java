package com.nunnun.wake.entity;

import com.nunnun.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.Check;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
        name = "wake_group_members",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"wake_group_id", "user_id"}),
                @UniqueConstraint(columnNames = {"wake_group_id", "slot_no"})
        }
)
@Check(constraints = "slot_no BETWEEN 1 AND 8")
@EntityListeners(AuditingEntityListener.class)
public class WakeGroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wake_group_id", nullable = false)
    private WakeGroup wakeGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "slot_no", nullable = false)
    private Short slotNo;

    @CreatedDate
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    protected WakeGroupMember() {
    }

    private WakeGroupMember(WakeGroup wakeGroup, User user, short slotNo) {
        this.wakeGroup = Objects.requireNonNull(wakeGroup);
        this.user = Objects.requireNonNull(user);
        this.slotNo = slotNo;
    }

    public static WakeGroupMember join(WakeGroup wakeGroup, User user, short slotNo) {
        return new WakeGroupMember(wakeGroup, user, slotNo);
    }

    public Long getId() {
        return id;
    }

    public WakeGroup getWakeGroup() {
        return wakeGroup;
    }

    public User getUser() {
        return user;
    }

    public Short getSlotNo() {
        return slotNo;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
}
