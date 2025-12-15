package org.example.demo2.model;

import java.io.Serializable;

/**
 * User model - phải implement Serializable để truyền qua RMI.
 */
public record User(
        Long id,
        String username,
        String displayName
) implements Serializable {
    private static final long serialVersionUID = 1L;
}