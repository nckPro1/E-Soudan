-- Stage 1: tài khoản và phiên đăng nhập. Xem docs/erd.md.

CREATE TABLE users (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email                VARCHAR(255) NOT NULL,
    password_hash        VARCHAR(100) NOT NULL,
    full_name            VARCHAR(100) NOT NULL,
    position             VARCHAR(100),
    system_role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    must_change_password BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           BIGINT,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by           BIGINT,
    version              BIGINT       NOT NULL DEFAULT 0,
    deleted_at           TIMESTAMPTZ,
    CONSTRAINT ck_users_system_role CHECK (system_role IN ('ADMIN', 'MANAGER', 'USER')),
    CONSTRAINT ck_users_status      CHECK (status IN ('ACTIVE', 'LOCKED')),
    CONSTRAINT fk_users_created_by  FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_users_updated_by  FOREIGN KEY (updated_by) REFERENCES users (id)
);

-- Email duy nhất không phân biệt hoa thường; chỉ tính tài khoản chưa xoá nên email dùng lại được sau khi xoá mềm.
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email)) WHERE deleted_at IS NULL;

CREATE TABLE refresh_token (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token (user_id);
