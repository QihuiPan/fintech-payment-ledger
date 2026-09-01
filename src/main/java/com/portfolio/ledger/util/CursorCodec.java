package com.portfolio.ledger.util;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import com.portfolio.ledger.domain.DomainException;

public final class CursorCodec {
    private CursorCodec() {
    }

    public static String encode(Instant createdAt, UUID entryId) {
        String raw = createdAt + "|" + entryId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new Cursor(null, null);
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException invalid) {
            throw DomainException.badRequest("INVALID_CURSOR", "Statement cursor is invalid");
        }
    }

    public record Cursor(Instant createdAt, UUID entryId) {
    }
}
