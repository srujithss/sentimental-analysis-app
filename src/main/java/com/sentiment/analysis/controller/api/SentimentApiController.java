package com.sentiment.analysis.controller.api;

import com.sentiment.analysis.dto.AnalysisResponse;
import com.sentiment.analysis.dto.AnalyzeRequest;
import com.sentiment.analysis.dto.HistoryPage;
import com.sentiment.analysis.service.PredictionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/** USER endpoints. SecurityConfig restricts /api/sentiment/** to ROLE_USER. */
@RestController
@RequestMapping("/api/sentiment")
public class SentimentApiController {

    private final PredictionService predictions;

    public SentimentApiController(PredictionService predictions) {
        this.predictions = predictions;
    }

    @PostMapping
    public AnalysisResponse analyze(@Valid @RequestBody AnalyzeRequest request, Principal principal) {
        return predictions.analyzeAndSave(request.text(), request.language(), principal.getName());
    }

    @GetMapping("/history")
    public HistoryPage history(@RequestParam(required = false) String language,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size,
                               Principal principal) {
        return predictions.history(principal.getName(), language, page, size);
    }
}
