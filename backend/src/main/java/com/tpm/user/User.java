package com.tpm.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    /** Global (system-wide) role. Per-project permissions live in {@code ProjectMember}. */
    public enum Role { USER, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    private boolean enabled = true;

    @Column(name = "email_notifications")
    private boolean emailNotifications = true;

    @Column(name = "avatar_color", nullable = false)
    private String avatarColor = "#6366f1";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {}

    public User(String email, String passwordHash, String fullName, Role role, String avatarColor) {
        this.email = email.trim().toLowerCase();
        this.passwordHash = passwordHash;
        this.fullName = fullName.trim();
        this.role = role;
        this.avatarColor = avatarColor;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public boolean isEmailNotifications() { return emailNotifications; }
    public String getAvatarColor() { return avatarColor; }
    public Instant getCreatedAt() { return createdAt; }

    public void setFullName(String fullName) { this.fullName = fullName.trim(); }
    public void setRole(Role role) { this.role = role; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setEmailNotifications(boolean emailNotifications) { this.emailNotifications = emailNotifications; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
