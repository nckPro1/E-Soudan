-- Lịch cá nhân: ghi chú đánh dấu theo ngày. Mỗi ghi chú thuộc riêng một người, không ai khác xem được.
CREATE TABLE calendar_note (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    note_date  DATE         NOT NULL,
    title      VARCHAR(100) NOT NULL,
    content    VARCHAR(2000),
    color      VARCHAR(10)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_calendar_note_user_date ON calendar_note (user_id, note_date);
