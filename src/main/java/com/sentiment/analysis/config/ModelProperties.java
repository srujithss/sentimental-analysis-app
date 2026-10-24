package com.sentiment.analysis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Binds the app.model.* section of application.yml. */
@ConfigurationProperties(prefix = "app.model")
public record ModelProperties(
        @DefaultValue("./models/xlmr-sentiment") String path,
        @DefaultValue("256") int maxLength,
        @DefaultValue("0.60") double lowConfidenceThreshold,
        @DefaultValue("true") boolean startupCheck) {
}
