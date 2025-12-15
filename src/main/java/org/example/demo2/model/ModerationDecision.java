package org.example.demo2.model;

public enum ModerationDecision {
    ALLOW,  // cho gửi bình thường
    WARN,   // vẫn gửi nhưng gắn nhãn cảnh báo
    BLOCK   // chặn, không phát tán
}