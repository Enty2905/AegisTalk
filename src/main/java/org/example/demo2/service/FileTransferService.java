package org.example.demo2.service;

import org.example.demo2.config.ServerConfig;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/**
 * Service để upload và download file qua HTTP.
 * Kết nối với FileHttpServerMain.
 */
public class FileTransferService {

    private final String serverUrl;

    public FileTransferService() {
        this.serverUrl = "http://" + ServerConfig.SERVER_HOST + ":" + ServerConfig.FILE_SERVER_PORT;
    }

    /**
     * Upload file lên server.
     *
     * @param file File cần upload
     * @param progressCallback Callback để báo tiến độ (0.0 - 1.0)
     * @return FileUploadResult chứa thông tin file sau khi upload
     * @throws IOException Nếu có lỗi I/O
     */
    public FileUploadResult uploadFile(File file, Consumer<Double> progressCallback) throws IOException {
        return uploadFile(file, null, progressCallback);
    }

    /**
     * Upload file lên server với uploader ID.
     *
     * @param file File cần upload
     * @param uploaderId ID của người upload (có thể null)
     * @param progressCallback Callback để báo tiến độ (0.0 - 1.0)
     * @return FileUploadResult chứa thông tin file sau khi upload
     * @throws IOException Nếu có lỗi I/O
     */
    public FileUploadResult uploadFile(File file, Long uploaderId, Consumer<Double> progressCallback) throws IOException {
        URL url = new URL(serverUrl + "/files");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", getMimeType(file));
            conn.setRequestProperty("X-Filename", file.getName());
            conn.setRequestProperty("Content-Length", String.valueOf(file.length()));
            if (uploaderId != null) {
                conn.setRequestProperty("X-Uploader-Id", uploaderId.toString());
            }

            // Upload với progress tracking
            long fileSize = file.length();
            long uploaded = 0;

            try (OutputStream os = conn.getOutputStream();
                 FileInputStream fis = new FileInputStream(file)) {

                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                    uploaded += bytesRead;

                    if (progressCallback != null) {
                        double progress = (double) uploaded / fileSize;
                        progressCallback.accept(progress);
                    }
                }
            }

            // Đọc response
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("Upload failed with code: " + responseCode);
            }

            // Parse JSON response
            String response;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                response = sb.toString();
            }

            return parseUploadResponse(response, file.getName());

        } finally {
            conn.disconnect();
        }
    }

    /**
     * Download file từ server.
     *
     * @param fileId ID của file trên server
     * @param targetDir Thư mục lưu file
     * @param progressCallback Callback để báo tiến độ (0.0 - 1.0)
     * @return File đã download
     * @throws IOException Nếu có lỗi I/O
     */
    public File downloadFile(long fileId, Path targetDir, Consumer<Double> progressCallback) throws IOException {
        return downloadFile(fileId, targetDir, null, progressCallback);
    }

    /**
     * Download file từ server với filename chỉ định.
     *
     * @param fileId ID của file trên server
     * @param targetDir Thư mục lưu file
     * @param suggestedFilename Tên file gợi ý (dùng nếu server không trả về filename)
     * @param progressCallback Callback để báo tiến độ (0.0 - 1.0)
     * @return File đã download
     * @throws IOException Nếu có lỗi I/O
     */
    public File downloadFile(long fileId, Path targetDir, String suggestedFilename, Consumer<Double> progressCallback) throws IOException {
        URL url = new URL(serverUrl + "/files/" + fileId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            if (responseCode == 404) {
                throw new IOException("File not found: " + fileId);
            }
            if (responseCode != 200) {
                throw new IOException("Download failed with code: " + responseCode);
            }

            // Lấy filename từ Content-Disposition header, hoặc dùng suggestedFilename, hoặc dùng fileId
            String filename = suggestedFilename != null ? suggestedFilename : "file_" + fileId;
            String contentDisposition = conn.getHeaderField("Content-Disposition");
            if (contentDisposition != null && contentDisposition.contains("filename=")) {
                String extracted = contentDisposition.substring(contentDisposition.indexOf("filename=") + 9).replace("\"", "");
                if (!extracted.isBlank()) {
                    filename = extracted;
                }
            }

            // Tạo thư mục nếu chưa có
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            // Download với progress tracking
            long contentLength = conn.getContentLengthLong();
            long downloaded = 0;

            Path targetFile = targetDir.resolve(filename);

            // Nếu file đã tồn tại, thêm số vào tên
            int counter = 1;
            while (Files.exists(targetFile)) {
                String name = filename;
                String ext = "";
                int dotIndex = filename.lastIndexOf('.');
                if (dotIndex > 0) {
                    name = filename.substring(0, dotIndex);
                    ext = filename.substring(dotIndex);
                }
                targetFile = targetDir.resolve(name + "_" + counter + ext);
                counter++;
            }

            try (InputStream is = conn.getInputStream();
                 FileOutputStream fos = new FileOutputStream(targetFile.toFile())) {

                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    downloaded += bytesRead;

                    if (progressCallback != null && contentLength > 0) {
                        double progress = (double) downloaded / contentLength;
                        progressCallback.accept(progress);
                    }
                }
            }

            return targetFile.toFile();

        } finally {
            conn.disconnect();
        }
    }

    /**
     * Lấy thông tin file mà không download.
     *
     * @param fileId ID của file
     * @return FileInfo hoặc null nếu không tìm thấy
     */
    public FileUploadResult getFileInfo(long fileId) throws IOException {
        URL url = new URL(serverUrl + "/files/" + fileId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("HEAD");

            int responseCode = conn.getResponseCode();
            if (responseCode == 404) {
                return null;
            }
            if (responseCode != 200) {
                throw new IOException("Request failed with code: " + responseCode);
            }

            String contentType = conn.getContentType();
            long size = conn.getContentLengthLong();
            String etag = conn.getHeaderField("ETag");

            return new FileUploadResult(fileId, "file_" + fileId, size, contentType, etag);

        } finally {
            conn.disconnect();
        }
    }

    /**
     * Parse JSON response từ upload.
     */
    private FileUploadResult parseUploadResponse(String json, String originalFilename) {
        // Simple JSON parsing (không dùng thư viện)
        // {"id":1,"filename":"test.txt","size":1234,"etag":"\"abc123\""}

        long id = 0;
        String filename = originalFilename;
        long size = 0;
        String etag = "";

        // Parse id
        int idIndex = json.indexOf("\"id\":");
        if (idIndex >= 0) {
            int start = idIndex + 5;
            int end = json.indexOf(",", start);
            if (end < 0) end = json.indexOf("}", start);
            id = Long.parseLong(json.substring(start, end).trim());
        }

        // Parse filename
        int fnIndex = json.indexOf("\"filename\":\"");
        if (fnIndex >= 0) {
            int start = fnIndex + 12;
            int end = json.indexOf("\"", start);
            filename = json.substring(start, end);
        }

        // Parse size
        int sizeIndex = json.indexOf("\"size\":");
        if (sizeIndex >= 0) {
            int start = sizeIndex + 7;
            int end = json.indexOf(",", start);
            if (end < 0) end = json.indexOf("}", start);
            size = Long.parseLong(json.substring(start, end).trim());
        }

        // Parse etag
        int etagIndex = json.indexOf("\"etag\":");
        if (etagIndex >= 0) {
            int start = etagIndex + 7;
            int end = json.indexOf("}", start);
            etag = json.substring(start, end).trim();
            // Remove quotes
            etag = etag.replace("\"", "").replace("\\", "");
        }

        return new FileUploadResult(id, filename, size, getMimeTypeFromFilename(filename), etag);
    }

    /**
     * Lấy MIME type từ file.
     */
    private String getMimeType(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".doc")) return "application/msword";
        if (name.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (name.endsWith(".xls")) return "application/vnd.ms-excel";
        if (name.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (name.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (name.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (name.endsWith(".zip")) return "application/zip";
        if (name.endsWith(".rar")) return "application/x-rar-compressed";
        if (name.endsWith(".7z")) return "application/x-7z-compressed";
        if (name.endsWith(".txt")) return "text/plain";
        if (name.endsWith(".html") || name.endsWith(".htm")) return "text/html";
        if (name.endsWith(".css")) return "text/css";
        if (name.endsWith(".js")) return "application/javascript";
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".xml")) return "application/xml";
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".wav")) return "audio/wav";
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".avi")) return "video/x-msvideo";
        if (name.endsWith(".mkv")) return "video/x-matroska";
        return "application/octet-stream";
    }

    private String getMimeTypeFromFilename(String filename) {
        return getMimeType(new File(filename));
    }

    /**
     * Kết quả upload file.
     */
    public static class FileUploadResult {
        private final long id;
        private final String filename;
        private final long size;
        private final String contentType;
        private final String etag;

        public FileUploadResult(long id, String filename, long size, String contentType, String etag) {
            this.id = id;
            this.filename = filename;
            this.size = size;
            this.contentType = contentType;
            this.etag = etag;
        }

        public long getId() { return id; }
        public String getFilename() { return filename; }
        public long getSize() { return size; }
        public String getContentType() { return contentType; }
        public String getEtag() { return etag; }

        /**
         * Kiểm tra có phải là hình ảnh không.
         */
        public boolean isImage() {
            return contentType != null && contentType.startsWith("image/");
        }

        /**
         * Format kích thước file.
         */
        public String getFormattedSize() {
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
            if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
            return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
        }

        /**
         * Lấy URL download.
         */
        public String getDownloadUrl() {
            return "http://" + ServerConfig.SERVER_HOST + ":" + ServerConfig.FILE_SERVER_PORT + "/files/" + id;
        }

        @Override
        public String toString() {
            return "FileUploadResult{" +
                    "id=" + id +
                    ", filename='" + filename + '\'' +
                    ", size=" + getFormattedSize() +
                    ", contentType='" + contentType + '\'' +
                    '}';
        }
    }
}
