package com.example.urlshortener.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortCodeGeneratorTest {

    private static final String BASE62 =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final ShortCodeGenerator generator = new ShortCodeGenerator();

    @Test
    void shouldGenerateSevenCharacterCode() {
        String shortCode = generator.generate();

        assertEquals(7, shortCode.length());
    }

    @Test
    void shouldGenerateOnlyBase62Characters() {
        String shortCode = generator.generate();

        assertTrue(
                shortCode.chars()
                        .allMatch(character -> BASE62.indexOf(character) >= 0)
        );
    }

    @Test
    void shouldGenerateMultipleCodesWithoutDuplicates() {
        Set<String> generatedCodes = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            generatedCodes.add(generator.generate());
        }

        assertEquals(100, generatedCodes.size());
    }
}
