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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "wake_groups")
@EntityListeners(AuditingEntityListener.class)
public class WakeGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "invite_code", unique = true, length = 6)
    private String inviteCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected WakeGroup() {
    }

    private WakeGroup(String name, String inviteCode, User creator) {
        this.name = Objects.requireNonNull(name);
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

    public User getCreator() {
        return creator;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
