package com.tpm.user;

import java.io.Serializable;
import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {}

    /** Compact user reference embedded in tasks, comments, members, etc. */
    public record UserSummary(Long id, String fullName, String email, String avatarColor) implements Serializable {
        public static UserSummary of(User u) {
            return u == null ? null : new UserSummary(u.getId(), u.getFullName(), u.getEmail(), u.getAvatarColor());
        }
    }

    public record UserProfile(Long id, String email, String fullName, User.Role role, String avatarColor,
            boolean emailNotifications, Instant createdAt) {
        public static UserProfile of(User u) {
            return new UserProfile(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getAvatarColor(),
                    u.isEmailNotifications(), u.getCreatedAt());
        }
    }

    public record AdminUserView(Long id, String email, String fullName, User.Role role, boolean enabled,
            String avatarColor, Instant createdAt) {
        public static AdminUserView of(User u) {
            return new AdminUserView(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.isEnabled(),
                    u.getAvatarColor(), u.getCreatedAt());
        }
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 100) String fullName,
            Boolean emailNotifications) {}

    public record AdminUpdateUserRequest(User.Role role, Boolean enabled) {}
}
