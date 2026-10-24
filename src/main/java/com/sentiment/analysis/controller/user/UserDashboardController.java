package com.sentiment.analysis.controller.user;

import com.sentiment.analysis.service.PredictionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class UserDashboardController {

    private final PredictionService predictions;

    public UserDashboardController(PredictionService predictions) {
        this.predictions = predictions;
    }

    @GetMapping("/user/dashboard")
    public String dashboard(Model model, Principal principal) {
        model.addAttribute("myAnalyses", predictions.countFor(principal.getName()));
        return "user/dashboard";
    }
}
