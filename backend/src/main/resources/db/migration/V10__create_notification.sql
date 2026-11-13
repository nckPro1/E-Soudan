-- Thông báo trong ứng dụng (mục "Hoạt động"). Mỗi dòng là một thông báo gửi cho đúng MỘT người nhận.
-- Lưu dữ liệu có cấu trúc (loại, người gây ra, đối tượng) thay vì câu chữ, để giao diện tự dịch theo ngôn ngữ người xem.

CREATE TABLE notification (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_id   BIGINT       NOT NULL,
    type           VARCHAR(40)  NOT NULL,
    actor_id       BIGINT,
    task_id        BIGINT,
    community_id   BIGINT,
    post_id        BIGINT,
    post_author_id BIGINT,
    subject        VARCHAR(300),
    snippet        VARCHAR(300),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at        TIMESTAMPTZ,
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES users (id),
    CONSTRAINT fk_notification_actor     FOREIGN KEY (actor_id) REFERENCES users (id)
);

-- Danh sách thông báo của một người, mới nhất trước
CREATE INDEX idx_notification_recipient ON notification (recipient_id, created_at DESC, id DESC);

-- Đếm nhanh số chưa đọc
CREATE INDEX idx_notification_unread ON notification (recipient_id) WHERE read_at IS NULL;
