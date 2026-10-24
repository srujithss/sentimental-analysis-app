package com.sentiment.analysis.service;

import com.sentiment.analysis.dto.LanguageInfo;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.Character.UnicodeScript.KANNADA;
import static java.lang.Character.UnicodeScript.LATIN;
import static java.lang.Character.UnicodeScript.MALAYALAM;
import static java.lang.Character.UnicodeScript.TAMIL;
import static java.lang.Character.UnicodeScript.TELUGU;

/**
 * Script-based detector for the Dravidian languages.
 *
 * Each of Tamil, Telugu, Kannada and Malayalam has its own Unicode script, so for native-script text
 * the script identifies the language exactly. Latin-script text cannot be told apart this way
 * (romanized Tamil and English both use Latin letters), so it is reported as "und-Latn".
 */
@Service
public class LanguageDetectionService {

    /** A second script must reach this share before text is flagged as code-mixed. */
    private static final double MIX_THRESHOLD = 0.15;

    private static final Map<Character.UnicodeScript, String[]> DRAVIDIAN = Map.of(
            TAMIL, new String[]{"ta", "Tamil"},
            TELUGU, new String[]{"te", "Telugu"},
            KANNADA, new String[]{"kn", "Kannada"},
            MALAYALAM, new String[]{"ml", "Malayalam"});

    /** Language code -> name, e.g. "ta" -> "Tamil"; the codes the language picker may send. */
    private static final Map<String, String> NAMES = DRAVIDIAN.values().stream()
            .collect(Collectors.toUnmodifiableMap(v -> v[0], v -> v[1]));

    public LanguageInfo detect(String text) {
        if (text == null || text.isBlank()) {
            return new LanguageInfo("und", "Unknown", "Unknown", false, 0.0);
        }

        Map<Character.UnicodeScript, Integer> dravidianCounts = new EnumMap<>(Character.UnicodeScript.class);
        int[] latin = {0};

        text.codePoints().forEach(cp -> {
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            if (script == LATIN) {
                if (Character.isLetter(cp)) {
                    latin[0]++;
                }
            } else if (DRAVIDIAN.containsKey(script)) {
                dravidianCounts.merge(script, 1, Integer::sum);
            }
        });

        int dravidianTotal = dravidianCounts.values().stream().mapToInt(Integer::intValue).sum();
        int total = dravidianTotal + latin[0];

        if (total == 0) {
            return new LanguageInfo("und", "Unknown", "Unknown", false, 0.0);
        }
        if (dravidianTotal == 0) {
            return new LanguageInfo("und-Latn", "Latin script (romanized Dravidian or English)",
                    "Latin", false, 1.0);
        }

        double dravidianShare = dravidianTotal / (double) total;

        if (latin[0] > dravidianTotal) {
            // Latin dominates; Dravidian characters present means code-mixing
            return new LanguageInfo("und-Latn", "Latin script (romanized Dravidian or English)",
                    "Latin", dravidianShare >= MIX_THRESHOLD, 1.0 - dravidianShare);
        }

        Map.Entry<Character.UnicodeScript, Integer> top = dravidianCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();
        String[] info = DRAVIDIAN.get(top.getKey());

        double latinShare = latin[0] / (double) total;
        double topShareOfDravidian = top.getValue() / (double) dravidianTotal;
        boolean codeMixed = latinShare >= MIX_THRESHOLD || topShareOfDravidian <= (1.0 - MIX_THRESHOLD);

        return new LanguageInfo(info[0], info[1], info[1], codeMixed, top.getValue() / (double) total);
    }

    /**
     * Applies the language the user picked on the Analyze page. Native script already identifies the
     * language exactly, so the pick is only used when the script cannot tell: Latin (romanized) or
     * unknown text. The detected script and code-mixing flag are kept as they are.
     */
    public LanguageInfo applySelection(LanguageInfo detected, String selectedCode) {
        String name = selectedCode == null ? null : NAMES.get(selectedCode);
        boolean scriptIsAmbiguous = detected.isLatin() || "und".equals(detected.code());
        if (name == null || !scriptIsAmbiguous) {
            return detected;
        }
        return new LanguageInfo(selectedCode, name, detected.script(), detected.codeMixed(), detected.scriptShare());
    }
}
