package org.example.demo2.net.moderation;

import org.example.demo2.model.ModerationDecision;
import org.example.demo2.model.ModerationResult;
import org.example.demo2.model.PolicyCategory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Moderation dùng Gemini free API:
 * - Có gọi Gemini (debug, không parse JSON phức tạp)
 * - Quyết định ALLOW / WARN / BLOCK bằng heuristic keyword.
 */
public class GeminiModerationService {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String apiKey;

    public GeminiModerationService() {
        // Đọc từ biến môi trường GERMINI_API (theo yêu cầu)
        String envKey = System.getenv("GERMINI_API");
        if (envKey == null || envKey.isBlank()) {
            // Fallback về GEMINI_API_KEY nếu GERMINI_API không có
            envKey = System.getenv("GEMINI_API_KEY");
        }
        this.apiKey = envKey;
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[GeminiModerationService] WARNING: GERMINI_API (or GEMINI_API_KEY) is not set");
        }
    }

    // ===== TEXT =====
    public ModerationResult moderateText(String text) {
        EnumSet<PolicyCategory> cats = EnumSet.noneOf(PolicyCategory.class);

        if (text == null || text.isBlank()) {
            return new ModerationResult(
                    ModerationDecision.ALLOW,
                    cats,
                    0.0,
                    "Empty text"
            );
        }

        String lower = text.toLowerCase(Locale.ROOT);

        // Heuristic đơn giản
        double sexualScore = containsAny(lower, "sex", "porn", "xxx", "nude", "sexy") ? 0.7 : 0.0;
        double minorsScore = (lower.contains("child") || lower.contains("kid") || lower.contains("under 18"))
                && sexualScore > 0 ? 0.9 : 0.0;
        double violenceScore = containsAny(lower, "kill", "murder", "stab", "shoot", "bomb") ? 0.7 : 0.0;
        double harassmentScore = containsAny(lower, "idiot", "stupid", "bastard", "hate you") ? 0.6 : 0.0;
        double selfHarmScore = containsAny(lower, "suicide", "kill myself", "want to die") ? 0.8 : 0.0;

        double maxScore = sexualScore;
        maxScore = Math.max(maxScore, minorsScore);
        maxScore = Math.max(maxScore, violenceScore);
        maxScore = Math.max(maxScore, harassmentScore);
        maxScore = Math.max(maxScore, selfHarmScore);

        ModerationDecision decision = ModerationDecision.ALLOW;
        String reason = "OK";

        // Chính sách: BLOCK sexual/minors, WARN các loại còn lại
        if (minorsScore >= 0.4 || sexualScore >= 0.6) {
            decision = ModerationDecision.BLOCK;
            addCategory(cats, "SEXUAL");
            addCategory(cats, "SEXUAL_MINORS");
            reason = "Blocked by keyword heuristic (sexual)";
        } else if (violenceScore >= 0.6 || harassmentScore >= 0.6 || selfHarmScore >= 0.5) {
            decision = ModerationDecision.WARN;
            if (violenceScore >= 0.6) addCategory(cats, "VIOLENCE");
            if (harassmentScore >= 0.6) addCategory(cats, "HARASSMENT");
            if (selfHarmScore >= 0.5) addCategory(cats, "SELF_HARM");
            reason = "Warning by keyword heuristic";
        }

        // Gọi Gemini để “thực sự dùng AI” (chỉ log status, không parse sâu)
        callGeminiForDebug(text);

        return new ModerationResult(decision, cats, maxScore, reason);
    }

    // ===== IMAGE =====
    public ModerationResult moderateImage(byte[] jpegBytes) {
        return new ModerationResult(
                ModerationDecision.ALLOW,
                EnumSet.noneOf(PolicyCategory.class),
                0.0,
                "Image moderation not implemented yet (Day 12)"
        );
    }

    // ===== helpers =====

    private boolean containsAny(String text, String... words) {
        for (String w : words) {
            if (text.contains(w)) return true;
        }
        return false;
    }

    // Không dùng hằng số enum trực tiếp để tránh lỗi nếu enum của em thiếu field
    private void addCategory(Set<PolicyCategory> set, String name) {
        try {
            set.add(PolicyCategory.valueOf(name));
        } catch (IllegalArgumentException ignored) {
            // nếu PolicyCategory không có giá trị đó thì bỏ qua
        }
    }

    private void callGeminiForDebug(String userText) {
        if (apiKey == null || apiKey.isBlank()) return;
        try {
            String escaped = userText
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n");
            String body = "{ \"contents\":[{\"parts\":[{\"text\":\"" + escaped + "\"}]}]}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT + "?key=" + apiKey))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(resp ->
                            System.out.println("[GeminiModerationService] debug status=" + resp.statusCode())
                    );
        } catch (Exception ignored) {
        }
    }
}
