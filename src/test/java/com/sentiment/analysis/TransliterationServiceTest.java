package com.sentiment.analysis;

import com.sentiment.analysis.service.TransliterationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransliterationServiceTest {

    private final TransliterationService service = new TransliterationService();

    @Test
    void supportsDravidianLanguages() {
        assertTrue(service.canTransliterate("ta"));
        assertTrue(service.canTransliterate("te"));
        assertTrue(service.canTransliterate("kn"));
        assertTrue(service.canTransliterate("ml"));
        assertFalse(service.canTransliterate("en"));
        assertFalse(service.canTransliterate(null));
    }

    @Test
    void transliteratesToTamilScript() {
        String result = service.transliterate("nalla", "ta");
        assertFalse(result.chars().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN),
                "Result should contain no Latin characters: " + result);
    }

    @Test
    void transliteratesToTeluguScript() {
        String result = service.transliterate("bagundi", "te");
        assertTrue(result.codePoints()
                        .filter(Character::isLetter)
                        .anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.TELUGU),
                "Result should contain Telugu characters: " + result);
    }

    @Test
    void transliteratesToKannadaScript() {
        String result = service.transliterate("chennagide", "kn");
        assertTrue(result.codePoints()
                        .filter(Character::isLetter)
                        .anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.KANNADA),
                "Result should contain Kannada characters: " + result);
    }

    @Test
    void transliteratesToMalayalamScript() {
        String result = service.transliterate("nannayirunnu", "ml");
        assertTrue(result.codePoints()
                        .filter(Character::isLetter)
                        .anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.MALAYALAM),
                "Result should contain Malayalam characters: " + result);
    }

    @Test
    void unsupportedLanguageReturnsOriginal() {
        assertEquals("hello world", service.transliterate("hello world", "en"));
    }

    @Test
    void blankTextReturnsAsIs() {
        assertEquals("", service.transliterate("", "ta"));
        assertEquals("   ", service.transliterate("   ", "ta"));
    }

    @Test
    void nullTextReturnsNull() {
        assertEquals(null, service.transliterate(null, "ta"));
    }
}
