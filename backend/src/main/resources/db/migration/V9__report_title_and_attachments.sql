-- Báo cáo có Tiêu đề riêng (ô trên) và Nội dung (ô dưới), và có thể đính kèm file, ảnh.

ALTER TABLE report_post ADD COLUMN title VARCHAR(200);

-- Báo cáo cũ chưa có tiêu đề: đặt theo ngày của báo cáo
UPDATE report_post SET title = 'Báo cáo ngày ' || to_char(report_date, 'DD/MM/YYYY');

ALTER TABLE report_post ALTER COLUMN title SET NOT NULL;

-- Nội dung có thể để trống nếu đã đính kèm file (service kiểm tra)
ALTER TABLE report_post ALTER COLUMN body DROP NOT NULL;

CREATE TABLE report_attachment (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id      BIGINT       NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    storage_key  VARCHAR(100) NOT NULL,
    uploaded_by  BIGINT       NOT NULL,
    uploaded_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_report_attachment_post     FOREIGN KEY (post_id) REFERENCES report_post (id),
    CONSTRAINT fk_report_attachment_uploader FOREIGN KEY (uploaded_by) REFERENCES users (id)
);

CREATE INDEX idx_report_attachment_post ON report_attachment (post_id, id);
