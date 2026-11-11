-- Spring Security's standard table for persisted OAuth2 clients (tokens).
-- Lets the app act for a partner who isn't currently logged in.
CREATE TABLE IF NOT EXISTS oauth2_authorized_client (
    client_registration_id  VARCHAR(100)  NOT NULL,
    principal_name          VARCHAR(200)  NOT NULL,
    access_token_type       VARCHAR(100)  NOT NULL,
    access_token_value      BLOB          NOT NULL,
    access_token_issued_at  TIMESTAMP     NOT NULL,
    access_token_expires_at TIMESTAMP     NOT NULL,
    access_token_scopes     VARCHAR(1000) DEFAULT NULL,
    refresh_token_value     BLOB          DEFAULT NULL,
    refresh_token_issued_at TIMESTAMP     DEFAULT NULL,
    created_at              TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (client_registration_id, principal_name)
);

-- Everyone who has logged in. Max 5 because of Spotify Development Mode.
CREATE TABLE IF NOT EXISTS app_user (
    spotify_id     VARCHAR(200)  PRIMARY KEY,
    display_name   VARCHAR(300)  NOT NULL,
    avatar_url     VARCHAR(1000),
    -- Whether other people may include this user (and so read their top tracks) in blends.
    allow_blending BOOLEAN       DEFAULT TRUE NOT NULL,
    last_login_at  TIMESTAMP     NOT NULL
);

-- One row per playlist the app manages.
CREATE TABLE IF NOT EXISTS blend (
    playlist_id   VARCHAR(100) PRIMARY KEY,
    playlist_name VARCHAR(300) NOT NULL,
    playlist_url  VARCHAR(500) NOT NULL,
    owner_id      VARCHAR(200) NOT NULL,
    time_range    VARCHAR(20)  NOT NULL,
    size          INT          NOT NULL,
    shared_mode   VARCHAR(20)  NOT NULL,
    track_count   INT          NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);

-- Everyone in a blend (1-5 people), owner included, in display order.
CREATE TABLE IF NOT EXISTS blend_member (
    playlist_id  VARCHAR(100) NOT NULL,
    spotify_id   VARCHAR(200) NOT NULL,
    member_order INT          NOT NULL,
    PRIMARY KEY (playlist_id, spotify_id),
    FOREIGN KEY (playlist_id) REFERENCES blend (playlist_id) ON DELETE CASCADE
);

-- The songs in the last build of each blend, in playlist order, and who each came from.
-- Powers the breakdown page; rewritten on every create/update.
CREATE TABLE IF NOT EXISTS blend_track (
    playlist_id  VARCHAR(100)  NOT NULL,
    track_order  INT           NOT NULL,
    track_id     VARCHAR(100)  NOT NULL,
    track_uri    VARCHAR(200)  NOT NULL,
    track_name   VARCHAR(500)  NOT NULL,
    artists      VARCHAR(1000) NOT NULL,
    contributors VARCHAR(1100) NOT NULL,
    image_url    VARCHAR(1000),
    PRIMARY KEY (playlist_id, track_order),
    FOREIGN KEY (playlist_id) REFERENCES blend (playlist_id) ON DELETE CASCADE
);

-- Weekly automatic updates. Added after the first deployment, so existing databases get the columns too.
ALTER TABLE blend ADD COLUMN IF NOT EXISTS auto_update BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE blend ADD COLUMN IF NOT EXISTS last_auto_update_at TIMESTAMP;
ALTER TABLE blend ADD COLUMN IF NOT EXISTS last_auto_update_failure VARCHAR(30);
ALTER TABLE blend ADD COLUMN IF NOT EXISTS last_auto_update_detail VARCHAR(200);

-- Shuffled order (still evenly spread per person). Existing blends were built in ranked order, so they default to off.
ALTER TABLE blend ADD COLUMN IF NOT EXISTS is_shuffled BOOLEAN DEFAULT FALSE NOT NULL;

-- When each background job last ran, so a run missed while the app was down can catch up.
CREATE TABLE IF NOT EXISTS job_run (
    name        VARCHAR(100) PRIMARY KEY,
    last_run_at TIMESTAMP    NOT NULL
);
