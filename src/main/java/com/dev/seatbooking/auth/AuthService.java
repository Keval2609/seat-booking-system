package com.dev.seatbooking.auth;

import com.dev.seatbooking.auth.dto.LoginRequest;
import com.dev.seatbooking.auth.dto.RegisterRequest;
import com.dev.seatbooking.auth.dto.TokenResponse;
import com.dev.seatbooking.auth.dto.UserResponse;
import com.dev.seatbooking.entity.Role;
import com.dev.seatbooking.entity.User;
import com.dev.seatbooking.error.ApiException;
import com.dev.seatbooking.repository.UserRepository;
import com.dev.seatbooking.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("dummy-password-for-timing");
    }

    public UserResponse register(RegisterRequest req) {
        String email = normalize(req.email());
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }
        try {
            User saved = users.saveAndFlush(new User(email, encoder.encode(req.password()), Role.USER));
            return new UserResponse(saved.getId(), saved.getEmail(), saved.getRole());
        } catch (DataIntegrityViolationException e) {
            throw emailTaken();
        }
    }

    public TokenResponse login(LoginRequest req) {
        User user = users.findByEmail(normalize(req.email())).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean ok = encoder.matches(req.password(), hash);
        if (user == null || !ok) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }
        return jwt.issue(user);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email already registered");
    }
}
