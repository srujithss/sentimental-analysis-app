package com.sentiment.analysis.security;

import com.sentiment.analysis.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AppUserRepository users;

    public LoginSuccessHandler(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication auth) throws IOException {
        users.findByUsername(auth.getName()).ifPresent(u -> {
            u.setLastLoginAt(Instant.now());
            users.save(u);
        });

        boolean admin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        response.sendRedirect(request.getContextPath() + (admin ? "/admin/dashboard" : "/user/dashboard"));
    }
}
