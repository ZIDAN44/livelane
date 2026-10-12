package com.tpm.user;

import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tpm.auth.AuthService;
import com.tpm.common.Exceptions.BadRequestException;
import com.tpm.common.Exceptions.ConflictException;
import com.tpm.common.Exceptions.NotFoundException;
import com.tpm.common.PageResponse;
import com.tpm.seed.DemoAccount;
import com.tpm.user.UserDtos.AdminUpdateUserRequest;
import com.tpm.user.UserDtos.AdminUserView;
import com.tpm.user.UserDtos.UpdateProfileRequest;
import com.tpm.user.UserDtos.UserProfile;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, AuthService authService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public UserProfile me() {
        return UserProfile.of(load(CurrentUser.id()));
    }

    @Transactional
    public UserProfile updateProfile(UpdateProfileRequest req) {
        var user = load(CurrentUser.id());
        user.setFullName(req.fullName());
        if (req.emailNotifications() != null) user.setEmailNotifications(req.emailNotifications());
        return UserProfile.of(user);
    }

    @Transactional
    public void changePassword(String current, String next) {
        var user = load(CurrentUser.id());
        if (DemoAccount.isDemo(user.getEmail())) {
            throw new ConflictException("Demo account passwords can't be changed - other visitors use them too");
        }
        if (!passwordEncoder.matches(current, user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(next));
        authService.revokeAllSessions(user.getId()); // sign out every other device
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserView> search(String q, Pageable pageable) {
        var query = q == null || q.isBlank() ? null : q.trim();
        return PageResponse.of(users.search(query, pageable), AdminUserView::of);
    }

    @Transactional
    public AdminUserView adminUpdate(Long userId, AdminUpdateUserRequest req) {
        if (userId == CurrentUser.id()) throw new BadRequestException("You can't change your own role or status");
        var user = load(userId);
        if (DemoAccount.isDemo(user.getEmail())) {
            throw new ConflictException("Demo accounts are protected so the demo keeps working for everyone");
        }
        if (req.role() != null) user.setRole(req.role());
        if (req.enabled() != null) {
            user.setEnabled(req.enabled());
            if (!req.enabled()) authService.revokeAllSessions(userId);
        }
        return AdminUserView.of(user);
    }

    private User load(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }
}
