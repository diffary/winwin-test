package com.winwin.authapi.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record Credentials(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RegisterResponse(UUID id, String email) {
    }

    public record TokenResponse(String token) {
    }
}
