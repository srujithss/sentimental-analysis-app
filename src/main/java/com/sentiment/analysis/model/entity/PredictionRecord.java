package com.sentiment.analysis.model.entity;

import com.sentiment.analysis.model.enums.Sentiment;
import jakarta.persistence.*;

import java.time.Instant;

/** One analysis run by a user. Always belongs to exactly one owner. */
@Entity
@Table(name = "prediction_record", indexes = @Index(name = "idx_prediction_owner", columnList = "owner_id"))
public class PredictionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    @Column(name = "input_text", length = 2000, nullable = false)
    private String text;

    private String languageCode;

    /** Language the user picked on the Analyze page; null when left on auto-detect. */
    private String selectedLanguage;

    private String script;
    private boolean codeMixed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Sentiment predictedLabel;

    private double confidence;
    private boolean lowConfidence;
    private long latencyMs;

    /** Set later when a user or the admin corrects the prediction. */
    @Enumerated(EnumType.STRING)
    private Sentiment correctedLabel;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getLanguageCode() { return languageCode; }
    public void setLanguageCode(String languageCode) { this.languageCode = languageCode; }
    public String getSelectedLanguage() { return selectedLanguage; }
    public void setSelectedLanguage(String selectedLanguage) { this.selectedLanguage = selectedLanguage; }
    public String getScript() { return script; }
    public void setScript(String script) { this.script = script; }
    public boolean isCodeMixed() { return codeMixed; }
    public void setCodeMixed(boolean codeMixed) { this.codeMixed = codeMixed; }
    public Sentiment getPredictedLabel() { return predictedLabel; }
    public void setPredictedLabel(Sentiment predictedLabel) { this.predictedLabel = predictedLabel; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public boolean isLowConfidence() { return lowConfidence; }
    public void setLowConfidence(boolean lowConfidence) { this.lowConfidence = lowConfidence; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public Sentiment getCorrectedLabel() { return correctedLabel; }
    public void setCorrectedLabel(Sentiment correctedLabel) { this.correctedLabel = correctedLabel; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
