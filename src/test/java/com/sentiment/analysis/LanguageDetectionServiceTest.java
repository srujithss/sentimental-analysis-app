package com.sentiment.analysis;

import com.sentiment.analysis.dto.LanguageInfo;
import com.sentiment.analysis.service.LanguageDetectionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageDetectionServiceTest {

    private final LanguageDetectionService service = new LanguageDetectionService();

    @Test
    void detectsTamil() {
        LanguageInfo info = service.detect("இந்த படம் மிகவும் அருமையாக இருந்தது");
        assertEquals("ta", info.code());
        assertEquals("Tamil", info.script());
        assertFalse(info.codeMixed());
    }

    @Test
    void detectsTelugu() {
        assertEquals("te", service.detect("ఈ సినిమా చాలా బాగుంది").code());
    }

    @Test
    void detectsKannada() {
        assertEquals("kn", service.detect("ಈ ಚಿತ್ರ ತುಂಬಾ ಚೆನ್ನಾಗಿದೆ").code());
    }

    @Test
    void detectsMalayalam() {
        assertEquals("ml", service.detect("ഈ സിനിമ വളരെ നന്നായിരുന്നു").code());
    }

    @Test
    void latinTextIsReportedAsLatinScript() {
        LanguageInfo info = service.detect("Indha padam romba nalla irukku");
        assertEquals("und-Latn", info.code());
        assertTrue(info.isLatin());
        assertFalse(info.codeMixed());
    }

    @Test
    void flagsCodeMixedText() {
        LanguageInfo info = service.detect("இந்த படம் super");
        assertEquals("ta", info.code());
        assertTrue(info.codeMixed());
    }

    @Test
    void digitsAndPunctuationOnlyIsUnknown() {
        assertEquals("und", service.detect("123 !!! ???").code());
    }

    @Test
    void selectedLanguageNamesRomanizedText() {
        LanguageInfo info = service.applySelection(service.detect("Indha padam romba nalla irukku"), "ta");
        assertEquals("ta", info.code());
        assertEquals("Tamil", info.name());
        assertEquals("Latin", info.script());
    }

    @Test
    void nativeScriptWinsOverSelectedLanguage() {
        LanguageInfo info = service.applySelection(service.detect("ഈ സിനിമ വളരെ നന്നായിരുന്നു"), "ta");
        assertEquals("ml", info.code());
    }

    @Test
    void noSelectionKeepsDetection() {
        LanguageInfo detected = service.detect("Indha padam romba nalla irukku");
        assertEquals(detected, service.applySelection(detected, null));
        assertEquals(detected, service.applySelection(detected, "xx"));
    }
}
