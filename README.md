# spotify-reblender

> **Note:** This project is an experiment, built entirely by an AI agent (Claude). The design, code, tests, CI and docs were all generated through conversation, with a human directing and reviewing. Treat it as a test project and review it before relying on it.

A self-hosted replacement for Spotify Blend. It builds a **deterministic, evenly balanced** two-person playlist from each user's Spotify top tracks, and can rebuild it on demand.

Blend often adds random recommendations or lets one person dominate. Reblender only uses each person's actual top tracks and splits the playlist evenly.

## How a playlist is built

Inputs: both users' top 50 tracks for the chosen range, a size N (2–100), and a shared-songs mode.

1. **Shared songs** (in both users' top lists) go first, ranked by combined position.
   - *Cap shared songs at a third* (default): at most N/3 shared songs.
   - *Let shared songs fill it*: shared songs can take every slot.
2. The rest is split **evenly** between the two users, each using their own top tracks in rank order.
3. **Odd slot:** it goes to one more shared song if there is one; otherwise the playlist is one song shorter so the split stays even.
4. If one person runs out of tracks, the other fills the gap. If both run out, leftover shared songs fill in.
5. Songs are **interleaved** (e.g. `S A B S A B …`) so nobody gets a long run.

Time ranges come from Spotify: **Recent** = `short_term` (~4 weeks), **All-time** = `long_term` (~1 year; Spotify has no true all-time).

## Spotify limits you should know

- New Spotify apps run in **Development Mode**: max **5 users**, each added by hand, and the app owner needs **Premium**.
- **Refresh tokens expire 6 months** after a user logs in. The app tells you who needs to log in again.
- Both people must log in to the app once; Spotify doesn't expose another user's top tracks.

## Setup

1. Create an app at <https://developer.spotify.com/dashboard> (Web API).
2. Add the redirect URI: `http://127.0.0.1:8080/login/oauth2/code/spotify`
   (use `127.0.0.1`, not `localhost`; for a hosted install use `https://your.domain/login/oauth2/code/spotify`).
3. Under **User Management**, add the Spotify email of everyone who will use it (you included).
4. `cp .env.example .env && chmod 600 .env`, then fill in the client ID and secret, and set `TOKEN_ENCRYPTION_KEY` to the output of `openssl rand -base64 32` (and `APP_BASE_URL` if not local).

## Run

With Docker:

```sh
docker compose up --build
```

Without Docker (JDK 21):

```sh
set -a; . ./.env; set +a   # same values as Docker, incl. a fixed TOKEN_ENCRYPTION_KEY
./gradlew bootRun
```

Open <http://127.0.0.1:8080>, log in, then have your partner do the same (on their device or after you log out). Pick them from the list, choose the range, size and shared mode, and create the playlist. Use **Update** to rebuild it with current top tracks.

Tests: `./gradlew test` (covers the blend engine).

## Storage and security

An H2 database file (`./data/` locally, the `reblender-data` volume in Docker) holds:

- `oauth2_authorized_client`: Spotify access and refresh tokens, so the app can read your partner's top tracks when they're not logged in.
- `app_user`: everyone who has logged in (Spotify ID and display name).
- `pairing`: each managed playlist and its settings.

How the tokens are protected:

- **Encrypted at rest** with AES-256-GCM (`TokenCipher`). Each ciphertext is bound to its row (Spotify user ID), so a token copied into another row won't decrypt, and tampering is detected. The key is never stored in the database (see *Where the key lives* below). The app refuses to start without a valid key.
- **Fails closed.** If Spring's storage format ever changes so a token can't be encrypted, the write is refused rather than stored in plaintext. A row that can't be decrypted (wrong key, tampering, or pre-encryption data) is deleted and that user is asked to log in again.
- **Least privilege.** Only the `user-top-read` and `playlist-modify-private` scopes are requested.
- **Never sent to the browser.** Tokens stay server-side; the browser only gets an HttpOnly, SameSite=Lax session cookie (set `SESSION_COOKIE_SECURE=true` behind HTTPS).
- **Container hardening.** It runs as a non-root user, with a read-only root filesystem and `no-new-privileges`. `/data` is only readable by the app user, and the port is bound to `127.0.0.1` by default.
- **Short-lived anyway.** Access tokens last an hour; Spotify refresh tokens expire 6 months after login.
- **Forget me.** The "Forget me" button deletes your stored tokens, your user record and every blend you're part of. To also revoke the app on Spotify's side, remove it at <https://www.spotify.com/account/apps/>.

### Where the key lives (pick one)

The app reads the key from `TOKEN_ENCRYPTION_KEY` (the key itself) **or** `TOKEN_ENCRYPTION_KEY_FILE` (a path to a file containing it). Setting both is an error.

| Option | Setup | A stolen copy of the disk/backup + config can decrypt tokens? |
|---|---|---|
| `.env` (default) | `TOKEN_ENCRYPTION_KEY=...` in `.env` | Yes, if `.env` is taken too |
| Docker secret | `deploy/docker-compose.secret.yml` | Yes, if the secret file is taken too |
| **TPM-sealed** (recommended for a permanent install) | `deploy/systemd/*.service` | **No**: the key only decrypts on this machine's TPM chip |

No option stops someone with **root on the running machine**: anything the app can decrypt, root can read out of its memory. The TPM option makes copies useless elsewhere; it can't protect a compromised host.

**Losing the key is harmless.** It only protects tokens, so if it's lost or changed (new machine, TPM reset) the old tokens are discarded and everyone just logs in again. No backup of the key is needed.

#### Docker secret

```sh
mkdir -p secrets && openssl rand -base64 32 > secrets/token_encryption_key
sudo chown 10001:10001 secrets/token_encryption_key && sudo chmod 400 secrets/token_encryption_key
# leave TOKEN_ENCRYPTION_KEY empty in .env
docker compose -f docker-compose.yml -f deploy/docker-compose.secret.yml up --build
```

#### Sealing the key to this machine (TPM)

This needs a TPM 2.0 (a VM can use a virtual TPM, e.g. Proxmox "Add > TPM State") and systemd 250 or newer.

```sh
systemd-creds has-tpm2            # should report the TPM as available
sudo mkdir -p /etc/credstore.encrypted
openssl rand -base64 32 | sudo systemd-creds encrypt --with-key=tpm2 \
    --name=reblender-token-key - /etc/credstore.encrypted/reblender-token-key
```

The key is generated and sealed in one step; it's never written anywhere unencrypted. Then pick a runner:

- **`deploy/systemd/spotify-reblender.service`** (strongest): runs the jar directly. systemd decrypts the key into a private in-memory directory and the app reads it via `TOKEN_ENCRYPTION_KEY_FILE`. The key never appears in `.env`, an environment variable or on disk. Install steps are in the file's header.
- **`deploy/systemd/spotify-reblender-docker.service`**: keeps Docker. The unit decrypts the key and passes it to the container as an environment variable, so it's on disk nowhere, but root can see it with `docker inspect`. Clone the repo to `/opt/spotify-reblender`, then follow the file's header.

In both cases leave `TOKEN_ENCRYPTION_KEY` empty in `.env`.

Rotating the key: replace it (re-run the `openssl ... | systemd-creds encrypt` line, or change the secret/`.env`) and restart. Existing tokens are discarded and everyone logs in again.

## Stack

Kotlin 2.3, Spring Boot 4.1 (Spring Security OAuth2 client, MVC, JDBC), Thymeleaf, H2, kotlinx.serialization, Docker.
