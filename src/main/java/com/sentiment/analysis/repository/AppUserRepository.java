package com.sentiment.analysis.repository;

import com.sentiment.analysis.model.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    long countByEnabled(boolean enabled);

    List<AppUser> findTop5ByOrderByCreatedAtDesc();
}
