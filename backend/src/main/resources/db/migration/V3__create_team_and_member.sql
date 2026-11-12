-- Stage 2: nhóm và thành viên nhóm. Xem docs/erd.md.

CREATE TABLE team (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  BIGINT,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  BIGINT,
    version     BIGINT       NOT NULL DEFAULT 0,
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT ck_team_status     CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_team_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_team_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

-- Tên nhóm duy nhất (không phân biệt hoa thường) trong số nhóm đang hoạt động; nhóm đã lưu trữ thì tên được dùng lại.
CREATE UNIQUE INDEX uq_team_name_active ON team (lower(name)) WHERE status = 'ACTIVE' AND deleted_at IS NULL;

CREATE TABLE team_member (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    team_id   BIGINT      NOT NULL,
    user_id   BIGINT      NOT NULL,
    team_role VARCHAR(20) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_team_member_role CHECK (team_role IN ('LEADER', 'MEMBER', 'OBSERVER')),
    CONSTRAINT uq_team_member      UNIQUE (team_id, user_id),
    CONSTRAINT fk_team_member_team FOREIGN KEY (team_id) REFERENCES team (id),
    CONSTRAINT fk_team_member_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_team_member_user_id ON team_member (user_id);
