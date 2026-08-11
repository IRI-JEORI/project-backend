package com.nunnun.roommate.entity;

import com.nunnun.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "roommate_behavior_manuals",
        uniqueConstraints = @UniqueConstraint(columnNames = {"roommate_group_id", "target_user_id"})
)
public class RoommateBehaviorManual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roommate_group_id", nullable = false)
    private RoommateGroup roommateGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected RoommateBehaviorManual() {
    }

    private RoommateBehaviorManual(RoommateGroup roommateGroup, User targetUser, String content, LocalDateTime generatedAt) {
        this.roommateGroup = Objects.requireNonNull(roommateGroup);
        this.targetUser = Objects.requireNonNull(targetUser);
        this.content = Objects.requireNonNull(content);
        this.generatedAt = Objects.requireNonNull(generatedAt);
        this.updatedAt = generatedAt;
    }

    public static RoommateBehaviorManual create(
            RoommateGroup roommateGroup, User targetUser, String content, LocalDateTime generatedAt
    ) {
        return new RoommateBehaviorManual(roommateGroup, targetUser, content, generatedAt);
    }

    public void updateContent(String content, LocalDateTime updatedAt) {
        this.content = Objects.requireNonNull(content);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public Long getId() {
        return id;
    }

    public RoommateGroup getRoommateGroup() {
        return roommateGroup;
    }

    public User getTargetUser() {
        return targetUser;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
