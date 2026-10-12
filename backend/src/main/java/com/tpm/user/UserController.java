package com.tpm.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tpm.common.PageResponse;
import com.tpm.user.UserDtos.AdminUpdateUserRequest;
import com.tpm.user.UserDtos.AdminUserView;
import com.tpm.user.UserDtos.UpdateProfileRequest;
import com.tpm.user.UserDtos.UserProfile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@Tag(name = "Users", description = "Profile and (admin) user management")
public class UserController {

    private final UserService users;
    private final JdbcClient jdbc;

    public UserController(UserService users, JdbcClient jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain at least one letter and one digit")
            String newPassword) {}

    public record SystemStats(long users, long projects, long tasks, long openTasks, long comments) {}

    @GetMapping("/api/users/me")
    public UserProfile me() {
        return users.me();
    }

    @PutMapping("/api/users/me")
    public UserProfile updateMe(@Valid @RequestBody UpdateProfileRequest req) {
        return users.updateProfile(req);
    }

    @PostMapping("/api/users/me/password")
    @Operation(summary = "Change password (signs out all other sessions)")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        users.changePassword(req.currentPassword(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Search all users (ADMIN)")
    public PageResponse<AdminUserView> search(@RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return users.search(q, pageable);
    }

    @PatchMapping("/api/admin/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Change a user's global role or enable/disable the account (ADMIN)")
    public AdminUserView adminUpdate(@PathVariable Long userId, @RequestBody AdminUpdateUserRequest req) {
        return users.adminUpdate(userId, req);
    }

    @GetMapping("/api/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "System-wide counters (ADMIN)")
    public SystemStats stats() {
        return jdbc.sql("""
                select (select count(*) from users) as users,
                       (select count(*) from projects) as projects,
                       (select count(*) from tasks) as tasks,
                       (select count(*) from tasks where completed_at is null) as open_tasks,
                       (select count(*) from comments) as comments
                """).query(SystemStats.class).single();
    }
}
