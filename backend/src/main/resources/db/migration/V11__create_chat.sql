-- Trò chuyện: nhắn riêng (DIRECT, cần đối phương chấp nhận lời mời) và chat nhóm (TEAM, mỗi nhóm một phòng chung).
-- Lịch sử tin nhắn được giữ vĩnh viễn: xoá tin chỉ là xoá mềm (nội dung bị ẩn khi hiển thị nhưng vẫn còn trong bảng).

CREATE TABLE chat_conversation (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type            VARCHAR(10) NOT NULL,
    -- TEAM: nhóm sở hữu phòng chat; thành viên là thành viên nhóm (không lưu lại ở đây)
    team_id         BIGINT,
    -- DIRECT: hai người, luôn lưu user_low_id < user_high_id để mỗi cặp chỉ có một cuộc trò chuyện
    user_low_id     BIGINT,
    user_high_id    BIGINT,
    -- DIRECT: PENDING (chờ đối phương chấp nhận) / ACCEPTED / DECLINED. TEAM luôn là ACCEPTED.
    status          VARCHAR(10) NOT NULL,
    requested_by    BIGINT,
    responded_at    TIMESTAMPTZ,
    last_message_id BIGINT,
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_chat_conversation_type   CHECK (type IN ('DIRECT', 'TEAM')),
    CONSTRAINT ck_chat_conversation_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED')),
    CONSTRAINT ck_chat_conversation_shape  CHECK (
        (type = 'TEAM' AND team_id IS NOT NULL AND user_low_id IS NULL AND user_high_id IS NULL)
        OR (type = 'DIRECT' AND team_id IS NULL AND user_low_id IS NOT NULL AND user_high_id IS NOT NULL
            AND user_low_id < user_high_id)),
    CONSTRAINT fk_chat_conversation_team      FOREIGN KEY (team_id) REFERENCES team (id),
    CONSTRAINT fk_chat_conversation_low       FOREIGN KEY (user_low_id) REFERENCES users (id),
    CONSTRAINT fk_chat_conversation_high      FOREIGN KEY (user_high_id) REFERENCES users (id),
    CONSTRAINT fk_chat_conversation_requester FOREIGN KEY (requested_by) REFERENCES users (id)
);

CREATE UNIQUE INDEX uq_chat_conversation_team   ON chat_conversation (team_id) WHERE team_id IS NOT NULL;
CREATE UNIQUE INDEX uq_chat_conversation_direct ON chat_conversation (user_low_id, user_high_id) WHERE type = 'DIRECT';
CREATE INDEX idx_chat_conversation_low  ON chat_conversation (user_low_id);
CREATE INDEX idx_chat_conversation_high ON chat_conversation (user_high_id);

CREATE TABLE chat_message (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT      NOT NULL,
    author_id       BIGINT      NOT NULL,
    body            TEXT,
    reply_to_id     BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at       TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT fk_chat_message_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversation (id),
    CONSTRAINT fk_chat_message_author       FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT fk_chat_message_reply        FOREIGN KEY (reply_to_id) REFERENCES chat_message (id)
);

-- Phân trang theo con trỏ: lấy N tin cũ hơn một id, mới nhất trước
CREATE INDEX idx_chat_message_conversation ON chat_message (conversation_id, id DESC);

ALTER TABLE chat_conversation
    ADD CONSTRAINT fk_chat_conversation_last_message FOREIGN KEY (last_message_id) REFERENCES chat_message (id);

CREATE TABLE chat_attachment (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_id   BIGINT       NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    storage_key  VARCHAR(100) NOT NULL,
    CONSTRAINT fk_chat_attachment_message FOREIGN KEY (message_id) REFERENCES chat_message (id)
);

CREATE INDEX idx_chat_attachment_message ON chat_attachment (message_id, id);

CREATE TABLE chat_reaction (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    emoji      VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_chat_reaction UNIQUE (message_id, user_id, emoji),
    CONSTRAINT fk_chat_reaction_message FOREIGN KEY (message_id) REFERENCES chat_message (id),
    CONSTRAINT fk_chat_reaction_user    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_chat_reaction_message ON chat_reaction (message_id);

-- Mỗi người đã đọc tới tin nào của mỗi cuộc trò chuyện (để đếm chưa đọc)
CREATE TABLE chat_read (
    conversation_id      BIGINT NOT NULL,
    user_id              BIGINT NOT NULL,
    last_read_message_id BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_chat_read_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversation (id),
    CONSTRAINT fk_chat_read_user         FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_chat_read_user ON chat_read (user_id);

-- Thông báo "lời mời nhắn tin" cần biết cuộc trò chuyện để mở đúng chỗ
ALTER TABLE notification ADD COLUMN conversation_id BIGINT;
