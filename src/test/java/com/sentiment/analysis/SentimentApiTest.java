package com.sentiment.analysis;

import com.sentiment.analysis.model.entity.AppUser;
import com.sentiment.analysis.model.entity.PredictionRecord;
import com.sentiment.analysis.model.enums.Role;
import com.sentiment.analysis.model.enums.Sentiment;
import com.sentiment.analysis.repository.AppUserRepository;
import com.sentiment.analysis.repository.PredictionRecordRepository;
import com.sentiment.analysis.service.SentimentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:apitest;DB_CLOSE_DELAY=-1",
        "app.model.startup-check=false"
})
@AutoConfigureMockMvc
class SentimentApiTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PredictionRecordRepository records;
    @Autowired SentimentService sentiment;

    private AppUser alice;
    private AppUser bob;

    @BeforeEach
    void seed() {
        records.deleteAll();
        alice = ensureUser("alice");
        bob = ensureUser("bob");
    }

    private AppUser ensureUser(String name) {
        return users.findByUsername(name).orElseGet(() -> {
            AppUser u = new AppUser();
            u.setUsername(name);
            u.setEmail(name + "@example.com");
            u.setPasswordHash("not-a-real-hash");
            u.setRole(Role.USER);
            return users.save(u);
        });
    }

    private void saveRecord(AppUser owner, String text) {
        saveRecord(owner, text, "ta");
    }

    private void saveRecord(AppUser owner, String text, String languageCode) {
        PredictionRecord p = new PredictionRecord();
        p.setOwner(owner);
        p.setText(text);
        p.setPredictedLabel(Sentiment.POSITIVE);
        p.setConfidence(0.9);
        p.setLanguageCode(languageCode);
        p.setScript("Tamil");
        records.save(p);
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void blankTextIsRejectedWithMessage() throws Exception {
        mvc.perform(post("/api/sentiment").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please enter some text"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void analyzeWorksWhenModelIsLoadedOtherwiseReturns503() throws Exception {
        int expected = sentiment.isReady() ? 200 : 503;
        mvc.perform(post("/api/sentiment").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Indha padam romba nalla irukku\"}"))
                .andExpect(status().is(expected));
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void unknownLanguageIsRejected() throws Exception {
        mvc.perform(post("/api/sentiment").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\",\"language\":\"fr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown language"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void pickedLanguageIsUsedForRomanizedTextAndSaved() throws Exception {
        assumeTrue(sentiment.isReady(), "model not exported");
        mvc.perform(post("/api/sentiment").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Indha padam romba nalla irukku\",\"language\":\"ta\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.languageCode").value("ta"))
                .andExpect(jsonPath("$.languageName").value("Tamil"))
                .andExpect(jsonPath("$.transliteratedText").isNotEmpty());

        PredictionRecord saved = records.findAll().getFirst();
        assertEquals("ta", saved.getLanguageCode());
        assertEquals("ta", saved.getSelectedLanguage());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCannotUseTheUserApi() throws Exception {
        mvc.perform(post("/api/sentiment").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void historyContainsOnlyTheCallersRecords() throws Exception {
        saveRecord(alice, "alice text");
        saveRecord(bob, "bob text");

        mvc.perform(get("/api/sentiment/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].text").value("alice text"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void historyCanBeFilteredByLanguage() throws Exception {
        saveRecord(alice, "tamil text", "ta");
        saveRecord(alice, "malayalam text", "ml");
        saveRecord(alice, "romanized text", "und-Latn");
        saveRecord(bob, "bob tamil text", "ta");

        mvc.perform(get("/api/sentiment/history").param("language", "ta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].text").value("tamil text"));
        mvc.perform(get("/api/sentiment/history").param("language", "und"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].text").value("romanized text"));
        mvc.perform(get("/api/sentiment/history").param("language", ""))
                .andExpect(jsonPath("$.totalItems").value(3));
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void unknownHistoryLanguageIsRejected() throws Exception {
        mvc.perform(get("/api/sentiment/history").param("language", "fr"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown language"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void pagesRender() throws Exception {
        mvc.perform(get("/user/analyze")).andExpect(status().isOk());
        mvc.perform(get("/user/history")).andExpect(status().isOk());
        mvc.perform(get("/user/dashboard")).andExpect(status().isOk());
    }
}
