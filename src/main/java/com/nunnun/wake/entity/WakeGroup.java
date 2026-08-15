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
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.Check;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "wake_groups")
@Check(constraints = "capacity IN (4, 8)")
@EntityListeners(AuditingEntityListener.class)
public class WakeGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private Short capacity;

    @Column(name = "invite_code", nullable = false, unique = true, length = 6)
    private String inviteCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected WakeGroup() {
    }

    private WakeGroup(String name, String inviteCode, User creator) {
        this.name = Objects.requireNonNull(name);
        this.capacity = (short) 4;
        this.inviteCode = Objects.requireNonNull(inviteCode);
        this.creator = Objects.requireNonNull(creator);
    }

    public static WakeGroup create(String name, String inviteCode, User creator) {
        return new WakeGroup(name, inviteCode, creator);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public Short getCapacity() {
        return capacity;
    }

    public User getCreator() {
        return creator;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
