package org.example.demo2.model;

import java.io.Serializable;
import java.util.EnumSet;

public class ModerationResult implements Serializable {

    private final ModerationDecision decision;
    private final EnumSet<PolicyCategory> categories;
    private final double maxScore;
    private final String reason;

    public ModerationResult(ModerationDecision decision,
                            EnumSet<PolicyCategory> categories,
                            double maxScore,
                            String reason) {
        this.decision = decision;
        this.categories = (categories == null
                ? EnumSet.noneOf(PolicyCategory.class)
                : EnumSet.copyOf(categories));
        this.maxScore = maxScore;
        this.reason = reason;
    }

    // ==== GETTER dùng cho controller / UI ====
    public ModerationDecision getDecision() {
        return decision;
    }

    public EnumSet<PolicyCategory> getCategories() {
        return EnumSet.copyOf(categories);
    }

    public double getMaxScore() {
        return maxScore;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "ModerationResult{" +
                "decision=" + decision +
                ", categories=" + categories +
                ", maxScore=" + maxScore +
                ", reason='" + reason + '\'' +
                '}';
    }
}
