-- Stage 3: task, lịch sử trạng thái, file nộp. Xem docs/erd.md.

CREATE TABLE task (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    team_id         BIGINT       NOT NULL,
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    assignee_id     BIGINT       NOT NULL,
    assigned_by     BIGINT       NOT NULL,
    due_date        DATE,
    priority        VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ASSIGNED',
    require_file    BOOLEAN      NOT NULL DEFAULT FALSE,
    revision_reason VARCHAR(1000),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT       NOT NULL DEFAULT 0,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT ck_task_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_task_status   CHECK (status IN ('ASSIGNED', 'IN_PROGRESS', 'PENDING_REVIEW', 'DONE')),
    CONSTRAINT fk_task_team     FOREIGN KEY (team_id) REFERENCES team (id),
    CONSTRAINT fk_task_assignee FOREIGN KEY (assignee_id) REFERENCES users (id),
    CONSTRAINT fk_task_assigner FOREIGN KEY (assigned_by) REFERENCES users (id),
    CONSTRAINT fk_task_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_task_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

CREATE INDEX idx_task_team_status ON task (team_id, status);
CREATE INDEX idx_task_assignee    ON task (assignee_id);

-- Chỉ thêm, không sửa: mỗi lần đổi trạng thái ghi một dòng (from_status NULL khi tạo mới).
CREATE TABLE task_history (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id     BIGINT      NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    changed_by  BIGINT      NOT NULL,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    note        VARCHAR(1000),
    CONSTRAINT ck_task_history_from CHECK (from_status IS NULL OR from_status IN ('ASSIGNED', 'IN_PROGRESS', 'PENDING_REVIEW', 'DONE')),
    CONSTRAINT ck_task_history_to   CHECK (to_status IN ('ASSIGNED', 'IN_PROGRESS', 'PENDING_REVIEW', 'DONE')),
    CONSTRAINT fk_task_history_task FOREIGN KEY (task_id) REFERENCES task (id),
    CONSTRAINT fk_task_history_user FOREIGN KEY (changed_by) REFERENCES users (id)
);

CREATE INDEX idx_task_history_task ON task_history (task_id, changed_at);

CREATE TABLE task_submission (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id      BIGINT       NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    storage_key  VARCHAR(64)  NOT NULL,
    uploaded_by  BIGINT       NOT NULL,
    uploaded_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_task_submission_key UNIQUE (storage_key),
    CONSTRAINT fk_task_submission_task FOREIGN KEY (task_id) REFERENCES task (id),
    CONSTRAINT fk_task_submission_user FOREIGN KEY (uploaded_by) REFERENCES users (id)
);

CREATE INDEX idx_task_submission_task ON task_submission (task_id);
