package com.tpm.notification;

import java.time.Instant;

import com.tpm.user.User;

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

@Entity
@Table(name = "notifications")
public class Notification {

    public enum Type { TASK_ASSIGNED, TASK_COMMENTED, TASK_DUE_SOON, ADDED_TO_PROJECT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false)
    private String message;

    private String link;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Notification() {}

    public Notification(User user, Type type, String message, String link) {
        this.user = user;
        this.type = type;
        this.message = message.length() > 500 ? message.substring(0, 497) + "..." : message;
        this.link = link;
    }

    public void markRead() { if (readAt == null) readAt = Instant.now(); }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Type getType() { return type; }
    public String getMessage() { return message; }
    public String getLink() { return link; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
