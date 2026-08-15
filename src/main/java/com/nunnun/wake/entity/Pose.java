package com.nunnun.wake.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "poses")
@EntityListeners(AuditingEntityListener.class)
public class Pose {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "image_object_key", nullable = false, length = 512)
    private String imageObjectKey;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean active;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Pose() {
    }

    private Pose(String code, String imageObjectKey, String description) {
        this.code = Objects.requireNonNull(code);
        this.imageObjectKey = Objects.requireNonNull(imageObjectKey);
        this.description = description;
        this.active = true;
    }

    public static Pose create(String code, String imageObjectKey, String description) {
        return new Pose(code, imageObjectKey, description);
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getImageObjectKey() { return imageObjectKey; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
