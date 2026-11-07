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
    spotify_id    VARCHAR(200) PRIMARY KEY,
    display_name  VARCHAR(300) NOT NULL,
    last_login_at TIMESTAMP    NOT NULL
);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(1000);

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
    PRIMARY KEY (playlist_id, track_order),
    FOREIGN KEY (playlist_id) REFERENCES blend (playlist_id) ON DELETE CASCADE
);
ALTER TABLE blend_track ADD COLUMN IF NOT EXISTS image_url VARCHAR(1000);
