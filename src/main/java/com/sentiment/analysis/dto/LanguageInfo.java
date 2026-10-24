package com.sentiment.analysis.dto;

/**
 * Result of script-based language detection.
 *
 * @param code        ISO code (ta, te, kn, ml) or "und-Latn" / "und"
 * @param name        human readable name
 * @param script      Tamil, Telugu, Kannada, Malayalam, Latin or Unknown
 * @param codeMixed   true when two scripts are both present in meaningful amounts
 * @param scriptShare share (0..1) of the dominant script among tracked characters
 */
public record LanguageInfo(String code, String name, String script, boolean codeMixed, double scriptShare) {

    /** Latin-script text: romanized Dravidian (e.g. Tanglish) or English. */
    public boolean isLatin() {
        return "Latin".equals(script);
    }
}
