package com.sentiment.analysis.controller.user;

import com.sentiment.analysis.service.SentimentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UserPagesController {

    private final SentimentService sentiment;

    public UserPagesController(SentimentService sentiment) {
        this.sentiment = sentiment;
    }

    @GetMapping("/user/analyze")
    public String analyze(Model model) {
        model.addAttribute("modelReady", sentiment.isReady());
        return "user/analyze";
    }

    @GetMapping("/user/history")
    public String history() {
        return "user/history";
    }
}
