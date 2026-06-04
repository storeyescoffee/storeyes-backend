CREATE SEQUENCE IF NOT EXISTS feedback_profile_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS feedback_profiles (
    id               BIGINT       NOT NULL DEFAULT nextval('feedback_profile_id_seq') PRIMARY KEY,
    store_id         BIGINT       NOT NULL UNIQUE REFERENCES stores(id),
    code             VARCHAR(100) NOT NULL UNIQUE,
    store_name       VARCHAR(255) NOT NULL,
    logo_url         VARCHAR(500),
    google_review_url VARCHAR(500) NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now()
);
