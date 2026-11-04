-- Profile photos kept in the database when Cloudinary is not configured.
CREATE TABLE profile_images (
    token         VARCHAR(40) PRIMARY KEY,
    company_id    BIGINT NOT NULL,
    owner_key     VARCHAR(40) NOT NULL,
    content_type  VARCHAR(40) NOT NULL,
    created_at    TIMESTAMP(6) NOT NULL,
    content       LONGBLOB NOT NULL
);
CREATE INDEX idx_profile_images_owner ON profile_images (company_id, owner_key);
