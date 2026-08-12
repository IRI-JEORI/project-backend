package com.nunnun.roommate.entity;

import com.nunnun.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "roommate_groups")
@EntityListeners(AuditingEntityListener.class)
public class RoommateGroup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 50) private String name;
    @Column(name = "invite_code", unique = true, length = 20) private String inviteCode;
    @Column(name = "invite_code_expires_at") private LocalDateTime inviteCodeExpiresAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "creator_id") private User creator;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RoommateGroupStatus status;
    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    protected RoommateGroup() {}

    private RoommateGroup(String name, String inviteCode, LocalDateTime expiresAt, User creator) {
        this.name = Objects.requireNonNull(name);
        this.inviteCode = Objects.requireNonNull(inviteCode);
        this.inviteCodeExpiresAt = Objects.requireNonNull(expiresAt);
        this.creator = Objects.requireNonNull(creator);
        this.status = RoommateGroupStatus.WAITING;
    }

    public static RoommateGroup create(String name, String inviteCode, LocalDateTime expiresAt, User creator) {
        return new RoommateGroup(name, inviteCode, expiresAt, creator);
    }

    public static RoommateGroup create(String name, String inviteCode, User creator) {
        return create(name, inviteCode, LocalDateTime.now(java.time.ZoneOffset.UTC).plusHours(24), creator);
    }

    public void activate() { status = RoommateGroupStatus.ACTIVE; }
    public void waitForRoommate() { status = RoommateGroupStatus.WAITING; }
    public boolean isInviteCodeExpiredAt(LocalDateTime now) {
        return inviteCodeExpiresAt == null || !now.isBefore(inviteCodeExpiresAt);
    }
    public void reissueInviteCode(String code, LocalDateTime expiresAt) {
        inviteCode = Objects.requireNonNull(code);
        inviteCodeExpiresAt = Objects.requireNonNull(expiresAt);
    }
    public void invalidateInviteCode() { inviteCode = null; inviteCodeExpiresAt = null; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getInviteCode() { return inviteCode; }
    public LocalDateTime getInviteCodeExpiresAt() { return inviteCodeExpiresAt; }
    public User getCreator() { return creator; }
    public RoommateGroupStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
