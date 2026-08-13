package com.nunnun.roommate.entity;

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
@Table(name = "roommate_complaints")
@EntityListeners(AuditingEntityListener.class)
public class RoommateComplaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roommate_group_id", nullable = false)
    private RoommateGroup roommateGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RoommateComplaint() {
    }

    private RoommateComplaint(RoommateGroup roommateGroup, User author, User targetUser, String content) {
        this.roommateGroup = Objects.requireNonNull(roommateGroup);
        this.author = Objects.requireNonNull(author);
        this.targetUser = Objects.requireNonNull(targetUser);
        this.content = Objects.requireNonNull(content);
    }

    public static RoommateComplaint create(RoommateGroup roommateGroup, User author, User targetUser, String content) {
        return new RoommateComplaint(roommateGroup, author, targetUser, content);
    }

    public void changeContent(String content) {
        this.content = Objects.requireNonNull(content);
    }

    public Long getId() {
        return id;
    }

    public RoommateGroup getRoommateGroup() {
        return roommateGroup;
    }

    public User getAuthor() {
        return author;
    }

    public User getTargetUser() {
        return targetUser;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
