package org.example.demo2.model;

public enum MessageType {
    TEXT,       // tin nhắn văn bản
    IMAGE,      // hình ảnh (payloadRef trỏ tới file)
    FILE,       // file bất kỳ
    SYSTEM,     // tin hệ thống (join/leave, thông báo...)
    CALL_EVENT  // sự kiện call voice/video
}
