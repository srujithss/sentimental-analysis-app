package com.sentiment.analysis;

import com.sentiment.analysis.dto.SentimentResult;
import com.sentiment.analysis.service.SentimentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Structural checks only (no accuracy assertions: the Phase 2 model is a zero-shot baseline).
 * Skipped automatically until the model has been exported to app.model.path.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:modeltest;DB_CLOSE_DELAY=-1",
        "app.model.startup-check=false"
})
class DjlSentimentServiceTest {

    @Autowired
    SentimentService service;

    @Test
    void returnsValidProbabilities() {
        assumeTrue(service.isReady(), "Model not exported yet; skipping");

        SentimentResult r = service.analyze("இந்த படம் மிகவும் அருமையாக இருந்தது");

        assertNotNull(r.label());
        assertEquals(3, r.probabilities().size());
        double sum = r.probabilities().values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 1e-3);
        assertTrue(r.confidence() > 0.0 && r.confidence() <= 1.0);
        assertEquals("ta", r.language().code());
    }

    @Test
    void romanizedTextAlsoWorks() {
        assumeTrue(service.isReady(), "Model not exported yet; skipping");

        SentimentResult r = service.analyze("Indha padam romba mosam");

        assertNotNull(r.label());
        assertTrue(r.language().isLatin());
    }
}
