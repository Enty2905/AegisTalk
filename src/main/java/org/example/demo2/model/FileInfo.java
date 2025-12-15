package org.example.demo2.model;

public record FileInfo(
        long id,
        Long uploaderId,      // cho phép null
        String filename,
        String contentType,
        long sizeBytes,
        String sha256,
        String storagePath,
        String etag
) {
}