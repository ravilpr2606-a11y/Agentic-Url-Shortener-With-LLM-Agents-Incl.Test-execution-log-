package com.example.urlshortener.repository;

import com.example.urlshortener.domain.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, UUID> {

    Optional<ShortUrl> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);
}