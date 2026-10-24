package com.sentiment.analysis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AnalyzeRequest(
        @NotBlank(message = "Please enter some text")
        @Size(max = 1000, message = "Text is too long (maximum 1000 characters)")
        String text,

        /** Optional language picked by the user (ta, te, kn, ml); absent or empty means auto-detect. */
        @Pattern(regexp = "^(ta|te|kn|ml)?$", message = "Unknown language")
        String language) {
}
