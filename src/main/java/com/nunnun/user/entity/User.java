package com.nunnun.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected User() {
    }

    private User(String nickname, String email, String passwordHash, String avatarUrl, boolean demo) {
        this.nickname = Objects.requireNonNull(nickname);
        this.email = Objects.requireNonNull(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.avatarUrl = avatarUrl;
        this.demo = demo;
    }

    public static User create(String nickname, String email, String passwordHash) {
        return new User(nickname, email, passwordHash, null, false);
    }

    public static User createDemo(String nickname, String email, String passwordHash, String avatarUrl) {
        return new User(nickname, email, passwordHash, avatarUrl, true);
    }

    public void changeNickname(String nickname) {
        this.nickname = Objects.requireNonNull(nickname);
    }

    public void softDelete(LocalDateTime deletedAt) {
        this.deletedAt = Objects.requireNonNull(deletedAt);
    }

    public void anonymize(String nickname, String email) {
        this.nickname = Objects.requireNonNull(nickname);
        this.email = Objects.requireNonNull(email);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public String getEmail() {
        return email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isDemo() {
        return demo;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
