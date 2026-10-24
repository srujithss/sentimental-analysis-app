package com.sentiment.analysis.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Cleans text before it reaches the model.
 *
 * Rules (the Python script python/export_model.py applies the SAME rules, so Java and Python
 * results can be compared):
 *  1. Unicode NFC normalization (Tamil/Telugu/Kannada/Malayalam can encode one letter in several ways)
 *  2. remove zero-width space (U+200B) and BOM (U+FEFF); keep ZWJ/ZWNJ because they matter in Indic scripts
 *  3. collapse all whitespace to single spaces
 *  4. @mentions -> "@user" and links -> "http" (the model was trained on tweets cleaned this way)
 *
 * It deliberately does NOT lowercase or strip diacritics.
 */
@Service
public class PreprocessingService {

    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Z}]+");
    private static final Pattern MENTION = Pattern.compile("@[A-Za-z0-9_]+");
    private static final Pattern URL = Pattern.compile("https?://\\S+|www\\.\\S+");

    public String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String text = Normalizer.normalize(raw, Normalizer.Form.NFC);
        text = text.replace("\u200B", "").replace("\uFEFF", "");
        text = WHITESPACE.matcher(text).replaceAll(" ");
        text = MENTION.matcher(text).replaceAll("@user");
        text = URL.matcher(text).replaceAll("http");
        return text.trim();
    }
}
