package com.nunnun.device.entity;

import com.nunnun.global.entity.BaseTimeEntity;
import com.nunnun.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "users_devices")
public class UserDevice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "fcm_token", nullable = false, unique = true, length = 512)
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DevicePlatform platform;

    protected UserDevice() {
    }

    private UserDevice(User user, String fcmToken, DevicePlatform platform) {
        this.user = Objects.requireNonNull(user);
        this.fcmToken = Objects.requireNonNull(fcmToken);
        this.platform = Objects.requireNonNull(platform);
    }

    public static UserDevice create(User user, String fcmToken, DevicePlatform platform) {
        return new UserDevice(user, fcmToken, platform);
    }

    public void updateOwner(User user) {
        this.user = Objects.requireNonNull(user);
    }

    public void updatePlatform(DevicePlatform platform) {
        this.platform = Objects.requireNonNull(platform);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public DevicePlatform getPlatform() {
        return platform;
    }
}
