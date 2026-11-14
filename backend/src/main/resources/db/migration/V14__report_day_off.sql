-- Ngày nghỉ của từng người: ngày nghỉ không bị tính là "chưa nộp báo cáo" và không nhận nhắc nộp.
CREATE TABLE report_day_off (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    day        DATE         NOT NULL,
    reason     VARCHAR(200),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_report_day_off UNIQUE (user_id, day)
);
