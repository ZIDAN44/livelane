package com.tpm.auth;

import com.tpm.user.UserDtos.UserProfile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @Schema(example = "alice@demo.tpm") @NotBlank @Email String email,
            @Schema(example = "Demo@1234") @NotBlank String password) {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain at least one letter and one digit")
            String password,
            @NotBlank @Size(max = 100) String fullName) {}

    /** Access token for the Authorization header; the refresh token travels in an httpOnly cookie. */
    public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserProfile user) {}
}
