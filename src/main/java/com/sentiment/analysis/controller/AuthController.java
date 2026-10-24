package com.sentiment.analysis.controller;

import com.sentiment.analysis.dto.RegisterRequest;
import com.sentiment.analysis.service.UserAccountService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    private final UserAccountService accounts;

    public AuthController(UserAccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/")
    public String home(Authentication auth) {
        boolean admin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return admin ? "redirect:/admin/dashboard" : "redirect:/user/dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("form", new RegisterRequest());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterRequest form, BindingResult result) {
        if (form.getPassword() != null && !form.getPassword().equals(form.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "mismatch", "Passwords do not match");
        }
        if (form.getUsername() != null && accounts.usernameTaken(form.getUsername().trim())) {
            result.rejectValue("username", "taken", "Username already taken");
        }
        if (form.getEmail() != null && accounts.emailTaken(form.getEmail().trim().toLowerCase())) {
            result.rejectValue("email", "taken", "Email already registered");
        }
        if (result.hasErrors()) {
            return "auth/register";
        }
        accounts.register(form);
        return "redirect:/login?registered";
    }
}
