-- Cộng đồng báo cáo: Manager tạo cộng đồng, thêm thành viên (gồm cả Leader); thành viên đăng báo cáo hằng ngày
-- lên feed chung của cộng đồng, mọi người trong cộng đồng đều thấy.

CREATE TABLE community (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    owner_id    BIGINT       NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  BIGINT,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  BIGINT,
    version     BIGINT       NOT NULL DEFAULT 0,
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT fk_community_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX idx_community_owner ON community (owner_id);

CREATE TABLE community_member (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    community_id BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    added_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_community_member UNIQUE (community_id, user_id),
    CONSTRAINT fk_community_member_community FOREIGN KEY (community_id) REFERENCES community (id),
    CONSTRAINT fk_community_member_user      FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_community_member_user ON community_member (user_id);

-- Mỗi người, mỗi cộng đồng, mỗi ngày một báo cáo.
CREATE TABLE report_post (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    community_id BIGINT        NOT NULL,
    author_id    BIGINT        NOT NULL,
    report_date  DATE          NOT NULL,
    done_text    VARCHAR(4000) NOT NULL,
    plan_text    VARCHAR(4000),
    issues_text  VARCHAR(4000),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by   BIGINT,
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by   BIGINT,
    version      BIGINT        NOT NULL DEFAULT 0,
    deleted_at   TIMESTAMPTZ,
    CONSTRAINT uq_report_post_day UNIQUE (community_id, author_id, report_date),
    CONSTRAINT fk_report_post_community FOREIGN KEY (community_id) REFERENCES community (id),
    CONSTRAINT fk_report_post_author    FOREIGN KEY (author_id) REFERENCES users (id)
);

CREATE INDEX idx_report_post_feed ON report_post (community_id, report_date DESC, id DESC);

CREATE TABLE report_comment (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id    BIGINT        NOT NULL,
    author_id  BIGINT        NOT NULL,
    parent_id  BIGINT,
    body       VARCHAR(2000) NOT NULL,
    edited_at  TIMESTAMPTZ,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by BIGINT,
    version    BIGINT        NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_report_comment_post   FOREIGN KEY (post_id) REFERENCES report_post (id),
    CONSTRAINT fk_report_comment_author FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT fk_report_comment_parent FOREIGN KEY (parent_id) REFERENCES report_comment (id)
);

CREATE INDEX idx_report_comment_post ON report_comment (post_id, created_at);

-- Biểu cảm trên báo cáo HOẶC trên bình luận (đúng một trong hai).
CREATE TABLE report_reaction (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id    BIGINT,
    comment_id BIGINT,
    user_id    BIGINT      NOT NULL,
    emoji      VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_report_reaction_target CHECK ((post_id IS NULL) <> (comment_id IS NULL)),
    CONSTRAINT fk_report_reaction_post    FOREIGN KEY (post_id) REFERENCES report_post (id),
    CONSTRAINT fk_report_reaction_comment FOREIGN KEY (comment_id) REFERENCES report_comment (id),
    CONSTRAINT fk_report_reaction_user    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE UNIQUE INDEX uq_report_reaction_post ON report_reaction (post_id, user_id, emoji) WHERE post_id IS NOT NULL;
CREATE UNIQUE INDEX uq_report_reaction_comment ON report_reaction (comment_id, user_id, emoji) WHERE comment_id IS NOT NULL;
