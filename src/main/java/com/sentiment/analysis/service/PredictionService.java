package com.sentiment.analysis.service;

import com.sentiment.analysis.dto.AnalysisResponse;
import com.sentiment.analysis.dto.HistoryPage;
import com.sentiment.analysis.dto.HistoryRow;
import com.sentiment.analysis.dto.LanguageInfo;
import com.sentiment.analysis.dto.SentimentResult;
import com.sentiment.analysis.model.entity.AppUser;
import com.sentiment.analysis.model.entity.PredictionRecord;
import com.sentiment.analysis.repository.AppUserRepository;
import com.sentiment.analysis.repository.PredictionRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PredictionService {

    private static final int MAX_STORED_TEXT = 2000;

    /** History filter value -> stored language codes. "und" covers text whose language was not identified. */
    private static final Map<String, List<String>> HISTORY_FILTERS = Map.of(
            "ta", List.of("ta"), "ml", List.of("ml"), "kn", List.of("kn"), "te", List.of("te"),
            "und", List.of("und", "und-Latn"));

    private final SentimentService sentiment;
    private final LanguageDetectionService languages;
    private final PreprocessingService preprocessing;
    private final TransliterationService transliteration;
    private final PredictionRecordRepository records;
    private final AppUserRepository users;

    public PredictionService(SentimentService sentiment,
                             LanguageDetectionService languages,
                             PreprocessingService preprocessing,
                             TransliterationService transliteration,
                             PredictionRecordRepository records,
                             AppUserRepository users) {
        this.sentiment = sentiment;
        this.languages = languages;
        this.preprocessing = preprocessing;
        this.transliteration = transliteration;
        this.records = records;
        this.users = users;
    }

    /**
     * Runs the model, then saves the result for this user. The username comes from the login, never from the request.
     * selectedLanguage is the optional language picked on the Analyze page (null = auto-detect).
     */
    public AnalysisResponse analyzeAndSave(String text, String selectedLanguage, String username) {
        AppUser owner = users.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Unknown user: " + username));

        String selected = selectedLanguage == null || selectedLanguage.isBlank() ? null : selectedLanguage;

        String textToAnalyze = text;
        String transliteratedText = null;
        if (selected != null && transliteration.canTransliterate(selected)) {
            String cleaned = preprocessing.clean(text);
            LanguageInfo detected = languages.detect(cleaned);
            if (detected.isLatin()) {
                transliteratedText = transliteration.transliterate(cleaned, selected);
                textToAnalyze = transliteratedText;
            }
        }

        SentimentResult result = sentiment.analyze(textToAnalyze);
        LanguageInfo language = languages.applySelection(result.language(), selected);

        PredictionRecord record = new PredictionRecord();
        record.setOwner(owner);
        String original = text.trim();
        record.setText(original.length() > MAX_STORED_TEXT ? original.substring(0, MAX_STORED_TEXT) : original);
        record.setLanguageCode(language.code());
        record.setSelectedLanguage(selected);
        record.setScript(language.script());
        record.setCodeMixed(language.codeMixed());
        record.setPredictedLabel(result.label());
        record.setConfidence(result.confidence());
        record.setLowConfidence(result.lowConfidence());
        record.setLatencyMs(result.latencyMs());
        records.save(record);

        return AnalysisResponse.from(record.getId(), result, language, transliteratedText);
    }

    /** language: null or empty for all records, otherwise a key of HISTORY_FILTERS. */
    public HistoryPage history(String username, String language, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        Page<PredictionRecord> result;
        if (language == null || language.isBlank()) {
            result = records.findByOwnerUsernameOrderByCreatedAtDesc(username, pageable);
        } else {
            List<String> codes = HISTORY_FILTERS.get(language);
            if (codes == null) {
                throw new IllegalArgumentException("Unknown language");
            }
            result = records.findByOwnerUsernameAndLanguageCodeInOrderByCreatedAtDesc(username, codes, pageable);
        }
        return new HistoryPage(
                result.getContent().stream().map(HistoryRow::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalPages(), result.getTotalElements());
    }

    public long countFor(String username) {
        return records.countByOwnerUsername(username);
    }
}
