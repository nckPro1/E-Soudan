-- Bình luận trên task. Xoá mềm để còn dấu vết khi Leader hoặc tác giả gỡ bình luận.

CREATE TABLE task_comment (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id    BIGINT        NOT NULL,
    author_id  BIGINT        NOT NULL,
    body       VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by BIGINT,
    version    BIGINT        NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_task_comment_task       FOREIGN KEY (task_id) REFERENCES task (id),
    CONSTRAINT fk_task_comment_author     FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT fk_task_comment_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_task_comment_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

CREATE INDEX idx_task_comment_task ON task_comment (task_id, created_at);
