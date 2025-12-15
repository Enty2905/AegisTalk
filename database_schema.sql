-- ========================================
-- AEGISTALK DATABASE SCHEMA
-- ========================================
-- Cấu trúc mới: Conversations-based architecture
-- Mỗi cuộc trò chuyện (1-1 hoặc nhóm) = 1 conversation

CREATE DATABASE IF NOT EXISTS aegistalk
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE aegistalk;

-- Tạo user DB cho ứng dụng
CREATE USER IF NOT EXISTS 'aegis'@'localhost' IDENTIFIED BY 'aegis_pw';
GRANT ALL PRIVILEGES ON aegistalk.* TO 'aegis'@'localhost';
FLUSH PRIVILEGES;

-- ========================================
-- BẢNG USERS
-- ========================================
CREATE TABLE users (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username     VARCHAR(50)     NOT NULL,
    password_hash VARCHAR(255)   NOT NULL,
    display_name VARCHAR(100)    NULL,
    email        VARCHAR(100)    NULL,
    status       ENUM('ACTIVE', 'BLOCKED') NOT NULL DEFAULT 'ACTIVE',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========================================
-- BẢNG CONVERSATIONS (thay thế rooms)
-- ========================================
-- Mỗi cuộc trò chuyện (1-1 hoặc nhóm) = 1 conversation
CREATE TABLE conversations (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    type             ENUM('DIRECT', 'GROUP') NOT NULL,
    title            VARCHAR(100)    NULL,  -- Tên nhóm, với DIRECT thì null
    created_by       BIGINT UNSIGNED NOT NULL,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_message_id  BIGINT UNSIGNED NULL,  -- ID tin nhắn cuối cùng
    extra_settings   JSON            NULL,  -- Quyền, emoji, màu sắc, theme,...
    PRIMARY KEY (id),
    KEY idx_conversations_created_by (created_by),
    KEY idx_conversations_updated_at (updated_at),
    KEY idx_conversations_type (type),
    CONSTRAINT fk_conversations_created_by
        FOREIGN KEY (created_by) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========================================
-- BẢNG CONVERSATION_PARTICIPANTS (thay thế room_members)
-- ========================================
-- Thành viên trong cuộc trò chuyện
CREATE TABLE conversation_participants (
    conversation_id   BIGINT UNSIGNED NOT NULL,
    user_id           BIGINT UNSIGNED NOT NULL,
    role              ENUM('MEMBER', 'ADMIN', 'OWNER') NOT NULL DEFAULT 'MEMBER',
    joined_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_read_msg_id  BIGINT UNSIGNED NULL,  -- Tin nhắn cuối mà user này đã đọc
    is_muted          BOOLEAN         NOT NULL DEFAULT FALSE,
    PRIMARY KEY (conversation_id, user_id),
    KEY idx_participants_user (user_id),
    KEY idx_participants_conversation (conversation_id),
    CONSTRAINT fk_participants_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_participants_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========================================
-- BẢNG MESSAGES
-- ========================================
CREATE TABLE messages (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversation_id     BIGINT UNSIGNED NOT NULL,
    sender_id           BIGINT UNSIGNED NOT NULL,
    type                ENUM('TEXT', 'IMAGE', 'VIDEO', 'FILE', 'STICKER', 'SYSTEM', 'CALL_EVENT') NOT NULL DEFAULT 'TEXT',
    content_text         TEXT            NULL,  -- Nội dung text
    content_payload      JSON            NULL,  -- Link file, metadata ảnh, reaction,...
    reply_to_message_id  BIGINT UNSIGNED NULL,  -- Nếu là reply một tin khác
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted          BOOLEAN         NOT NULL DEFAULT FALSE,
    edited_at           DATETIME        NULL,  -- Nếu có sửa tin nhắn
    PRIMARY KEY (id),
    KEY idx_messages_conversation_created (conversation_id, created_at),
    KEY idx_messages_sender (sender_id),
    KEY idx_messages_reply_to (reply_to_message_id),
    KEY idx_messages_created_at (created_at),
    CONSTRAINT fk_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_messages_sender
        FOREIGN KEY (sender_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_messages_reply_to
        FOREIGN KEY (reply_to_message_id) REFERENCES messages(id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========================================
-- BẢNG MESSAGE_STATUS
-- ========================================
-- Trạng thái gửi/đọc (delivered/read)
CREATE TABLE message_status (
    message_id  BIGINT UNSIGNED NOT NULL,
    user_id     BIGINT UNSIGNED NOT NULL,
    status      ENUM('SENT', 'DELIVERED', 'READ') NOT NULL DEFAULT 'SENT',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (message_id, user_id),
    KEY idx_status_user (user_id),
    KEY idx_status_message (message_id),
    CONSTRAINT fk_status_message
        FOREIGN KEY (message_id) REFERENCES messages(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_status_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========================================
-- BẢNG FRIENDS & FRIEND_REQUESTS
-- ========================================
CREATE TABLE friend_requests (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    from_user_id BIGINT UNSIGNED NOT NULL,
    to_user_id   BIGINT UNSIGNED NOT NULL,
    status       ENUM('PENDING', 'ACCEPTED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_friend_requests_pair (from_user_id, to_user_id),
    KEY idx_friend_requests_from (from_user_id),
    KEY idx_friend_requests_to (to_user_id),
    CONSTRAINT fk_friend_requests_from
        FOREIGN KEY (from_user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_friend_requests_to
        FOREIGN KEY (to_user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE friends (
    user_id     BIGINT UNSIGNED NOT NULL,
    friend_id   BIGINT UNSIGNED NOT NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, friend_id),
    KEY idx_friends_user (user_id),
    KEY idx_friends_friend (friend_id),
    CONSTRAINT fk_friends_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_friends_friend
        FOREIGN KEY (friend_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========================================
-- BẢNG FILES (cho file upload/download)
-- ========================================
CREATE TABLE files (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uploader_id   BIGINT UNSIGNED NULL,
    filename      VARCHAR(255)    NOT NULL,
    content_type  VARCHAR(100)    NOT NULL,
    size_bytes    BIGINT UNSIGNED NOT NULL,
    sha256        CHAR(64)        NOT NULL,
    storage_path  VARCHAR(255)    NOT NULL,
    etag          VARCHAR(64)     NOT NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_files_uploader (uploader_id),
    UNIQUE KEY uk_files_sha256 (sha256),
    CONSTRAINT fk_files_uploader
        FOREIGN KEY (uploader_id) REFERENCES users(id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========================================
-- BẢNG MODERATION_LOGS
-- ========================================
CREATE TABLE moderation_logs (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id      BIGINT UNSIGNED NOT NULL,
    conversation_id BIGINT UNSIGNED NULL,
    message_id   BIGINT UNSIGNED NULL,
    file_id      BIGINT UNSIGNED NULL,
    target_type  ENUM('TEXT', 'IMAGE') NOT NULL,
    decision     ENUM('ALLOW', 'WARN', 'BLOCK') NOT NULL,
    categories   VARCHAR(255)   NULL,
    scores_json  JSON           NULL,
    hash         CHAR(64)       NULL,
    created_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_mod_logs_user_time (user_id, created_at),
    KEY idx_mod_logs_conversation_time (conversation_id, created_at),
    KEY idx_mod_logs_decision (decision),
    CONSTRAINT fk_mod_logs_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_mod_logs_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_mod_logs_message
        FOREIGN KEY (message_id) REFERENCES messages(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_mod_logs_file
        FOREIGN KEY (file_id) REFERENCES files(id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========================================
-- BẢNG PRESENCE_EVENTS (cho multicast presence)
-- ========================================
CREATE TABLE presence_events (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    conversation_id BIGINT UNSIGNED NULL,
    event_type  ENUM('JOIN', 'LEAVE', 'TYPING', 'ONLINE', 'OFFLINE') NOT NULL,
    ip_address  VARCHAR(45)     NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_presence_user_time (user_id, created_at),
    KEY idx_presence_conversation_time (conversation_id, created_at),
    CONSTRAINT fk_presence_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_presence_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations(id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========================================
-- INDEXES BỔ SUNG ĐỂ TỐI ƯU PERFORMANCE
-- ========================================
-- Index cho tìm conversation DIRECT giữa 2 user
-- (sẽ được tạo trong code khi cần)

-- ========================================
-- DỮ LIỆU MẪU (tùy chọn)
-- ========================================
-- INSERT INTO users (username, password_hash, display_name, email)
-- VALUES
-- ('thai', SHA2('123', 256), 'Nguyen Dinh Thai', 'thai@example.com'),
-- ('user2', SHA2('123', 256), 'User 2', 'user2@example.com'),
-- ('user3', SHA2('123', 256), 'User 3', 'user3@example.com');
