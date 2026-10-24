package com.sentiment.analysis;

import com.sentiment.analysis.service.PreprocessingService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreprocessingServiceTest {

    private final PreprocessingService service = new PreprocessingService();

    @Test
    void collapsesWhitespaceAndTrims() {
        assertEquals("a b c", service.clean("  a \t b\n\n c  "));
    }

    @Test
    void replacesMentionsAndLinks() {
        assertEquals("@user thanks http", service.clean("@some_one thanks https://example.com/x?y=1"));
        assertEquals("see http", service.clean("see www.example.com"));
    }

    @Test
    void removesZeroWidthSpaceButKeepsJoiners() {
        assertEquals("ab", service.clean("a\u200Bb"));
        assertEquals("a\u200Db", service.clean("a\u200Db"));   // ZWJ matters in Indic scripts
    }

    @Test
    void normalizesToNfc() {
        // "e" + combining acute accent  ->  single precomposed character
        assertEquals("\u00E9", service.clean("e\u0301"));
    }

    @Test
    void handlesNullAndBlank() {
        assertEquals("", service.clean(null));
        assertEquals("", service.clean("   "));
    }

    @Test
    void doesNotLowercase() {
        assertEquals("Super Padam", service.clean("Super   Padam"));
    }
}
