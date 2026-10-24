package com.sentiment.analysis.repository;

import com.sentiment.analysis.model.entity.PredictionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface PredictionRecordRepository extends JpaRepository<PredictionRecord, Long> {

    /** User area: ONLY ever query by owner. */
    Page<PredictionRecord> findByOwnerUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    Page<PredictionRecord> findByOwnerUsernameAndLanguageCodeInOrderByCreatedAtDesc(
            String username, Collection<String> languageCodes, Pageable pageable);

    long countByOwnerUsername(String username);
}
