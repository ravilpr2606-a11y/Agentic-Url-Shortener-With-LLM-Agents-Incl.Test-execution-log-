package com.example.urlshortener.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class ShortCodeGenerator {

    private static final String BASE62 =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final int CODE_LENGTH = 7;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder shortCode = new StringBuilder(CODE_LENGTH);

        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = random.nextInt(BASE62.length());
            shortCode.append(BASE62.charAt(index));
        }

        return shortCode.toString();
    }
}