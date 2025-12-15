package org.example.demo2.model;

import java.io.Serializable;

/**
 * Room model - phải implement Serializable để truyền qua RMI.
 */
public record Room(
        Long id,
        String name,
        String type,   // "PUBLIC" / "PRIVATE" / "DIRECT"
        Long createdByUserId
) implements Serializable {
    private static final long serialVersionUID = 1L;
}