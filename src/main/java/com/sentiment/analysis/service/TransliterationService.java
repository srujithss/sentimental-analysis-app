package com.sentiment.analysis.service;

import com.ibm.icu.text.Transliterator;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * Transliterates Latin-script (romanized) Dravidian text into its native script using ICU4J,
 * with post-processing to handle informal romanization patterns (Tanglish, Manglish, etc.).
 *
 * ICU's standard Latin-to-Indic transforms use IAST conventions. Informal romanization differs
 * in several ways (no diacritics, different consonant mappings). Post-processing fixes the most
 * common mismatches, especially the anusvara-to-nasal conversion in Tamil.
 */
@Service
public class TransliterationService {

    private static final Map<String, String> TRANSFORM_IDS = Map.of(
            "ta", "Latin-Tamil",
            "te", "Latin-Telugu",
            "kn", "Latin-Kannada",
            "ml", "Latin-Malayalam");

    private static final Set<String> SUPPORTED = TRANSFORM_IDS.keySet();

    public boolean canTransliterate(String languageCode) {
        return languageCode != null && SUPPORTED.contains(languageCode);
    }

    public String transliterate(String text, String languageCode) {
        String transformId = TRANSFORM_IDS.get(languageCode);
        if (transformId == null || text == null || text.isBlank()) {
            return text;
        }
        String normalized = normalizeInformalRomanization(text, languageCode);
        Transliterator t = Transliterator.getInstance(transformId);
        String result = t.transliterate(normalized);
        return postProcess(result, languageCode);
    }

    /**
     * Pre-processes informal romanization to improve ICU4J transliteration quality.
     * Handles common patterns that don't have IAST equivalents.
     */
    private static String normalizeInformalRomanization(String text, String languageCode) {
        if ("ta".equals(languageCode)) {
            // Tamil "zh" → IAST "ḻ" (ழ, the retroflex approximant unique to Tamil)
            text = text.replace("zh", "ḻ");
            text = text.replace("Zh", "Ḻ");
            text = text.replace("ZH", "Ḻ");
        }
        return text;
    }

    /**
     * Tamil uses specific nasal consonants, not anusvara. ICU4J's IAST-based transform produces
     * anusvara (ஂ) before stops; standard Tamil orthography uses the matching nasal + pulli instead.
     */
    private static String postProcess(String text, String languageCode) {
        if ("ta".equals(languageCode)) {
            text = fixTamilAnusvara(text);
        }
        return text;
    }

    private static String fixTamilAnusvara(String text) {
        // anusvara + velar → velar nasal
        text = text.replace("ஂக", "ங்க"); // ஂக → ங்க
        // anusvara + palatal → palatal nasal
        text = text.replace("ஂச", "ஞ்ச"); // ஂச → ஞ்ச
        // anusvara + retroflex → retroflex nasal
        text = text.replace("ஂட", "ண்ட"); // ஂட → ண்ட
        // anusvara + dental → dental nasal
        text = text.replace("ஂத", "ந்த"); // ஂத → ந்த
        // anusvara + labial → labial nasal
        text = text.replace("ஂப", "ம்ப"); // ஂப → ம்ப
        // remaining anusvara → labial nasal (most common default in Tamil)
        text = text.replace("ஂ", "ம்");              // ஂ → ம்
        return text;
    }
}
