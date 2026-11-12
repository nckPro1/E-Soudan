-- Bình luận: trả lời (một cấp), đã sửa, và thả biểu cảm (emoji).

ALTER TABLE task_comment
    ADD COLUMN parent_id BIGINT,
    ADD COLUMN edited_at TIMESTAMPTZ,
    ADD CONSTRAINT fk_task_comment_parent FOREIGN KEY (parent_id) REFERENCES task_comment (id);

CREATE INDEX idx_task_comment_parent ON task_comment (parent_id);

-- Mỗi người thả một emoji nhiều nhất một lần trên mỗi bình luận.
CREATE TABLE comment_reaction (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    comment_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    emoji      VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_comment_reaction UNIQUE (comment_id, user_id, emoji),
    CONSTRAINT fk_comment_reaction_comment FOREIGN KEY (comment_id) REFERENCES task_comment (id),
    CONSTRAINT fk_comment_reaction_user    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_comment_reaction_comment ON comment_reaction (comment_id);
