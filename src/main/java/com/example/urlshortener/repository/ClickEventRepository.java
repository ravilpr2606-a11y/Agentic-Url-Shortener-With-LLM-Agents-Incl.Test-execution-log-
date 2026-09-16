package com.example.urlshortener.repository;

import com.example.urlshortener.domain.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ClickEventRepository extends JpaRepository<ClickEvent, UUID> {

    long countByShortUrlId(UUID shortUrlId);

    @Query("""
            SELECT MAX(c.clickedAt)
            FROM ClickEvent c
            WHERE c.shortUrl.id = :shortUrlId
            """)
    Instant findLastAccessedAt(@Param("shortUrlId") UUID shortUrlId);
}