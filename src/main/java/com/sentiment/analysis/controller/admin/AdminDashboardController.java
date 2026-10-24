package com.sentiment.analysis.controller.admin;

import com.sentiment.analysis.repository.AppUserRepository;
import com.sentiment.analysis.service.SentimentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminDashboardController {

    private final AppUserRepository users;
    private final SentimentService sentiment;

    public AdminDashboardController(AppUserRepository users, SentimentService sentiment) {
        this.users = users;
        this.sentiment = sentiment;
    }

    @GetMapping("/admin/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("totalUsers", users.count());
        model.addAttribute("activeUsers", users.countByEnabled(true));
        model.addAttribute("recentUsers", users.findTop5ByOrderByCreatedAtDesc());
        model.addAttribute("modelReady", sentiment.isReady());
        model.addAttribute("modelPath", sentiment.modelPath());
        return "admin/dashboard";
    }
}
