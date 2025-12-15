package org.example.demo2.net.files;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.example.demo2.dao.FileDao;
import org.example.demo2.model.FileInfo;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.HexFormat;

public class FileHttpServerMain {

    private static final int PORT = 8081;
    private static final Path DATA_DIR = Paths.get("data");  // sẽ lưu data/<sha256>
 
    public static void main(String[] args) throws Exception {
        if (!Files.exists(DATA_DIR)) {
            Files.createDirectories(DATA_DIR);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/files", new FileHandler());
        server.setExecutor(null);
        System.out.println("[FileServer] Listening on http://localhost:" + PORT);
        server.start();
    }

    static class FileHandler implements HttpHandler {
        private final FileDao fileDao = new FileDao();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath(); // /files hoặc /files/{id}

                if ("POST".equalsIgnoreCase(method) && "/files".equals(path)) {
                    handleUpload(exchange);
                } else if (("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method))
                        && path.startsWith("/files/")) {
                    handleDownload(exchange, "HEAD".equalsIgnoreCase(method));
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            } catch (Exception e) {
                e.printStackTrace();
                byte[] body = ("Internal error: " + e.getMessage()).getBytes();
                exchange.sendResponseHeaders(500, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            } finally {
                exchange.close();
            }
        }

        private void handleUpload(HttpExchange exchange) throws IOException, SQLException, NoSuchAlgorithmException {
            // Lấy metadata
            String filename = exchange.getRequestHeaders().getFirst("X-Filename");
            if (filename == null || filename.isBlank()) filename = "upload.bin";

            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || contentType.isBlank()) contentType = "application/octet-stream";

            // Lấy uploader ID từ header (bắt buộc phải có)
            String uploaderIdStr = exchange.getRequestHeaders().getFirst("X-Uploader-Id");
            Long uploaderId = 1L; // Default to user ID 1 if not provided
            if (uploaderIdStr != null && !uploaderIdStr.isBlank()) {
                try {
                    uploaderId = Long.parseLong(uploaderIdStr);
                } catch (NumberFormatException ignored) {}
            }

            // Đọc toàn bộ body vào byte[]
            byte[] data;
            try (InputStream is = exchange.getRequestBody();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                is.transferTo(baos);
                data = baos.toByteArray();
            }

            long size = data.length;

            // Tính SHA-256
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String sha256 = HexFormat.of().formatHex(md.digest(data));
            String etag = sha256;  // Lưu vào DB không có dấu ngoặc kép

            // Nếu file trùng sha256 đã có thì dùng lại
            FileInfo info = fileDao.findBySha256(sha256);
            long id;
            if (info != null) {
                id = info.id();
            } else {
                Path storedPath = DATA_DIR.resolve(sha256);
                Files.write(storedPath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                String storagePath = storedPath.toString();
                id = fileDao.insert(uploaderId, filename, contentType, size, sha256, storagePath, etag);
            }

            // HTTP ETag cần có dấu ngoặc kép
            String httpEtag = "\"" + etag + "\"";
            String json = """
                    {"id":%d,"filename":"%s","size":%d,"etag":"%s"}
                    """.formatted(id, escapeJson(filename), size, etag);

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("ETag", httpEtag);
            byte[] body = json.getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }

        private void handleDownload(HttpExchange exchange, boolean headOnly) throws IOException, SQLException {
            String path = exchange.getRequestURI().getPath(); // /files/{id}
            String[] parts = path.split("/");
            if (parts.length != 3) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            long id;
            try {
                id = Long.parseLong(parts[2]);
            } catch (NumberFormatException e) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            FileInfo info = fileDao.findById(id);
            if (info == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            // Kiểm tra If-None-Match (HTTP ETag cần có dấu ngoặc kép)
            String clientEtag = exchange.getRequestHeaders().getFirst("If-None-Match");
            String dbEtag = info.etag();
            if (dbEtag == null || dbEtag.isBlank()) {
                dbEtag = info.sha256();
            }
            String httpEtag = "\"" + dbEtag + "\"";

            if (clientEtag != null && clientEtag.equals(httpEtag)) {
                exchange.getResponseHeaders().add("ETag", httpEtag);
                exchange.sendResponseHeaders(304, -1); // Not Modified
                return;
            }

            exchange.getResponseHeaders().add("ETag", httpEtag);
            exchange.getResponseHeaders().add("Content-Type",
                    info.contentType() != null ? info.contentType() : "application/octet-stream");
            exchange.getResponseHeaders().add("Content-Length", String.valueOf(info.sizeBytes()));

            if (headOnly) {
                // chỉ header, không body
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            Path pathOnDisk = Paths.get(info.storagePath());
            if (!Files.exists(pathOnDisk)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            exchange.sendResponseHeaders(200, info.sizeBytes());
            try (OutputStream os = exchange.getResponseBody()) {
                Files.copy(pathOnDisk, os);
            }
        }

        private String escapeJson(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
