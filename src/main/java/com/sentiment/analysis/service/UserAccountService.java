package com.sentiment.analysis.service;

import com.sentiment.analysis.dto.RegisterRequest;
import com.sentiment.analysis.model.entity.AppUser;
import com.sentiment.analysis.model.enums.Role;
import com.sentiment.analysis.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public UserAccountService(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public boolean usernameTaken(String username) {
        return users.existsByUsername(username);
    }

    public boolean emailTaken(String email) {
        return users.existsByEmail(email);
    }

    /** Public registration ALWAYS creates a USER. The role is never read from the request. */
    @Transactional
    public AppUser register(RegisterRequest req) {
        AppUser u = new AppUser();
        u.setUsername(req.getUsername().trim());
        u.setEmail(req.getEmail().trim().toLowerCase());
        u.setPasswordHash(encoder.encode(req.getPassword()));
        u.setRole(Role.USER);
        u.setEnabled(true);
        return users.save(u);
    }
}
