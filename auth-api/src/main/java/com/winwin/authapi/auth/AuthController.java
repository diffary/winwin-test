package com.winwin.authapi.auth;

import com.winwin.authapi.auth.AuthDtos.Credentials;
import com.winwin.authapi.auth.AuthDtos.RegisterResponse;
import com.winwin.authapi.auth.AuthDtos.TokenResponse;
import com.winwin.authapi.common.ApiExceptions.EmailTakenException;
import com.winwin.authapi.user.User;
import com.winwin.authapi.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwt) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody Credentials request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailTakenException();
        }
        User saved = users.save(new User(email, passwordEncoder.encode(request.password())));
        return new RegisterResponse(saved.getId(), saved.getEmail());
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody Credentials request) {
        // Same error for unknown email and wrong password, so emails can't be enumerated.
        User user = users.findByEmail(normalize(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("bad credentials"));
        return new TokenResponse(jwt.issue(user.getId()));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
