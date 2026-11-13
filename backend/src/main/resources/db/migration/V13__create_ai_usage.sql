-- Nhật ký lượt dùng trợ lý AI (giới hạn lượt mỗi giờ, truy vết). Không lưu nội dung câu hỏi/câu trả lời.
CREATE TABLE ai_usage (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (id),
    kind         VARCHAR(20) NOT NULL,
    source_count INT         NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_ai_usage_user_time ON ai_usage (user_id, created_at);
