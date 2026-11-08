# spotify-reblender

> **Note:** This project is an experiment, built entirely by an AI agent (Claude). The design, code, tests, CI and docs were all generated through conversation, with a human directing and reviewing. Treat it as a test project and review it before relying on it.

A self-hosted replacement for Spotify Blend. It builds a **deterministic, evenly balanced** playlist for 1 to 5 people from each person's Spotify top tracks, and can rebuild it on demand.

Blend often adds random recommendations or lets one person dominate. Reblender only uses each person's actual top tracks and splits the playlist evenly.

## How a playlist is built

A blend is you plus **0–4 other people** who have logged in. With nobody else, you get a playlist of your own top tracks (handy for testing on your own).

Inputs: everyone's top 50 tracks for the chosen range, a size N, and a shared-songs mode. N can be anything from **1 song per person up to 50 per person** (1–50 solo, 2–100 for two, up to 5–250 for five), since Spotify gives at most 50 top tracks per person. Playlists over 100 songs are written to Spotify in batches of 100.

1. **Shared songs** are songs in two or more people's top lists. Songs shared by **everyone** come first, then by one fewer person, and so on down to pairs. Within a tier they're ranked by average position.
2. **Shared budget** (*Cap shared songs by group size*, the default):

   | People | Shared songs take up to |
   |---|---|
   | 1 | none |
   | 2 | 1/3 |
   | 3 | 1/2 |
   | 4 | 2/3 |
   | 5 | 4/5 |

   *Let shared songs fill it* lets shared songs take every slot. Either way, only songs that really are shared count; if there aren't enough, more slots go to individual picks.
3. The rest is split **evenly** between everyone, using songs only they have, in rank order.
4. **Uneven slots** (e.g. 7 left for 2 people): they go to more shared songs if any are left; otherwise the playlist is slightly shorter so the split stays even.
5. Everyone gets the **same number** of their own songs. If someone has fewer than their share, everyone is capped at that number; spare shared songs fill the freed slots, otherwise the playlist is shorter.
6. Songs are **interleaved** (e.g. `S A B S A B …`) so nobody gets a long run.

Time ranges are Spotify's three windows: **Recent** = `short_term` (~4 weeks), **Half-year** = `medium_term` (~6 months), **All-time** = `long_term` (~1 year; Spotify has no true all-time). A short window can have fewer than 50 top tracks if you haven't listened to many different songs; the success message says when that shortened a playlist.

## Spotify limits you should know

- New Spotify apps run in **Development Mode**. That means at most **5 people**, each added by hand in the Spotify dashboard, and the app owner needs **Premium**. Nobody can sign themselves up. Someone who isn't on the list gets through Spotify's login page, but Spotify then refuses the app access, and Reblender explains this on its sign-in page.
- **Spotify logins expire 6 months** after someone logs in. The app tells you who needs to log in again.
- Everyone in a blend must log in to the app once; Spotify doesn't expose another user's top tracks.

## Setup and deployment

### 1. Create the Spotify app

1. Go to <https://developer.spotify.com/dashboard> and click **Create app**.
2. Fill in a name and description (anything, e.g. "Reblender").
3. Under **Redirect URIs**, add one for every address the app will run at:
   - local: `http://127.0.0.1:8080/login/oauth2/code/spotify` (use `127.0.0.1`, not `localhost`: Spotify only allows plain `http` for loopback addresses)
   - hosted: `https://reblender.example.com/login/oauth2/code/spotify`
4. Under **Which API/SDKs are you planning to use?**, tick **Web API**, agree to the terms and save.
5. Open the app's **Settings**: copy the **Client ID**, then click **View client secret** and copy that too.
6. Under **User Management**, add the name and Spotify account email of everyone who will use it, **including yourself**.

### 2. Configure

Settings come from environment variables. Locally they go in `.env` (`cp .env.example .env && chmod 600 .env`); on a server, use whatever your compose setup uses.

| Variable | Required | Suggested value | What it does |
|---|---|---|---|
| `SPOTIFY_CLIENT_ID` | Yes | from step 1 | Identifies the app to Spotify. |
| `SPOTIFY_CLIENT_SECRET` | Yes | from step 1 | Keep it out of git and out of compose files you share. |
| `TOKEN_ENCRYPTION_KEY` | One of these two | `openssl rand -base64 32` | Encrypts the stored Spotify logins. Losing or changing it just means everyone logs in again. |
| `TOKEN_ENCRYPTION_KEY_FILE` | One of these two | `/run/secrets/token_encryption_key` | The same key, read from a file (Docker secret or TPM-sealed; see [Where the key lives](#where-the-key-lives-pick-one)). Recommended on a server. |
| `APP_BASE_URL` | Yes when hosted | `https://reblender.example.com` | The public address. It must match the redirect URI from step 1. `https://` also makes the session cookie Secure. Defaults to `http://127.0.0.1:8080`. |
| `FORWARD_HEADERS_STRATEGY` | Behind a reverse proxy | `native` | Trusts the proxy's `X-Forwarded-*` headers, but only from private-network addresses. Leave it unset when there's no proxy. |
| `TZ` | No | e.g. `Europe/London` | Time zone for the "updated" times. |
| `AUTO_UPDATE_CRON` | No | leave the default (`0 0 6 * * MON`) | When the weekly update runs, as a Spring cron expression (second minute hour day month weekday). |
| `AUTO_UPDATE_ZONE` | No | leave the default (`Europe/London`) | Time zone for `AUTO_UPDATE_CRON`. |
| `DATABASE_URL` | No | leave the default | The H2 database file. Docker uses `/data/reblender` in the `reblender-data` volume; without Docker it's `./data/reblender`. |
| `SESSION_COOKIE_SECURE` | No | leave unset | Forces the Secure cookie flag on or off. Normally worked out from `APP_BASE_URL`. |

### 3. Run it locally

With Docker, building from this checkout:

```sh
docker compose up --build
```

Without Docker (JDK 21):

```sh
set -a; . ./.env; set +a
./gradlew bootRun
```

Open <http://127.0.0.1:8080> and log in. To blend with others, each of them logs in too. Tick who to include (or nobody for a solo playlist), choose the range, size and shared mode, and create the playlist. **Update** rebuilds it with current top tracks.

Tests and lint: `./gradlew build`.

### Weekly updates

Every Monday at 06:00 UK time, the app rebuilds each blend that has **Update weekly** switched on (the default; the switch is on the blend's breakdown page). It goes through them one at a time:
- **Failures:** if one blend can't be updated, the others still are. The reason is shown in the list and on the breakdown page (for example "weekly update skipped: Sam needs to log in again").
- **Spotify's limit:** if it's hit, the rest wait until the next week.
- **Missed runs:** if the app was off when a run was due, it catches up a minute after it next starts.

The schedule can be changed with `AUTO_UPDATE_CRON` and `AUTO_UPDATE_ZONE`.

### 4. Deploy on a server

Every merge to `master` publishes `ghcr.io/chesire/spotify-reblender:latest`, plus a `sha-<commit>` tag for pinning or rolling back. A typical setup runs it behind a TLS reverse proxy on the same Docker network, with no published port.

1. **DNS:** point a name at the server, e.g. `reblender.example.com`, as a CNAME to a name that already has the server's IP.
2. **Key:** create it as a file only the container's user (UID `10001`) can read:
   ```sh
   mkdir -p reblender
   openssl rand -base64 32 > reblender/token_encryption_key
   sudo chown 10001:10001 reblender/token_encryption_key && sudo chmod 400 reblender/token_encryption_key
   ```
3. **Settings:** put them in `reblender/reblender.env` (`chmod 600`):
   ```
   SPOTIFY_CLIENT_ID=...
   SPOTIFY_CLIENT_SECRET=...
   APP_BASE_URL=https://reblender.example.com
   FORWARD_HEADERS_STRATEGY=native
   TZ=Europe/London
   ```
4. **Compose service:**
   ```yaml
   secrets:
     reblender_token_key:
       file: ./reblender/token_encryption_key

   services:
     reblender:
       image: ghcr.io/chesire/spotify-reblender:latest
       container_name: reblender
       env_file: ./reblender/reblender.env
       environment:
         - TOKEN_ENCRYPTION_KEY_FILE=/run/secrets/reblender_token_key
       secrets:
         - reblender_token_key
       volumes:
         - reblender-data:/data
       read_only: true
       tmpfs:
         - /tmp
       security_opt:
         - no-new-privileges:true
       restart: unless-stopped

   volumes:
     reblender-data:
   ```
5. **Reverse proxy:** send `https://reblender.example.com` to `http://reblender:8080`. For example, with [SWAG](https://docs.linuxserver.io/general/swag/), add `reblender` to `SUBDOMAINS` so the certificate covers it, and create `config/nginx/site-confs/reblender.subdomain.conf`:
   ```nginx
   server {
       listen 443 ssl http2;
       listen [::]:443 ssl http2;
       server_name reblender.*;
       include /config/nginx/ssl.conf;
       client_max_body_size 0;

       location / {
           include /config/nginx/proxy.conf;
           resolver 127.0.0.11 valid=30s;
           set $upstream_proto http;
           set $upstream_app reblender;
           set $upstream_port 8080;
           proxy_pass $upstream_proto://$upstream_app:$upstream_port;
       }
   }
   ```
6. **Start it:** `docker compose up -d`, then check `docker compose logs -f reblender` for `Started ReblenderApplication`.

**Updating:** `docker compose pull reblender && docker compose up -d reblender`.

### Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| "Spotify won't let this app use your account yet" | That person isn't on the app's **User Management** list (step 1.6), or the list is full. |
| Spotify shows "INVALID_CLIENT: Invalid redirect URI" | The redirect URI in the dashboard doesn't exactly match `APP_BASE_URL` + `/login/oauth2/code/spotify`. |
| A certificate error **only from home** (e.g. issued by your router) | The router doesn't loop traffic for your public IP back inside ("hairpin NAT"). Add local DNS records (e.g. in Pi-hole) pointing the name at the server's LAN IP. |
| 502 Bad Gateway | The proxy can't reach the container: check `docker compose ps` and the logs. A missing or unreadable key file stops it starting. |

## Logs

Everything goes to the console (`docker compose logs -f`). Each request the app receives and each call it makes to Spotify gets one line:

```
IN  POST /playlists -> 302 (1840 ms) user=abc123
OUT GET https://api.spotify.com/v1/me/top/tracks?time_range=short_term&limit=50 -> 200 (212 ms, 48213 bytes)
OUT DELETE https://api.spotify.com/v1/me/library?uris=spotify:playlist:… -> 403 (95 ms): {"error": {"status": 403, "message": "Insufficient client scope" } }
```

Tokens are never logged:
- Headers aren't logged, so no cookies, CSRF tokens or `Authorization` headers.
- Request bodies aren't logged. These include the CSRF token, and on token requests the authorization code, PKCE verifier and refresh token.
- Successful response bodies aren't logged, since token responses contain the tokens.
- Query values are only shown for an allowlist of harmless parameters; everything else (like the OAuth `code` and `state`) shows as `***`.
- Error bodies from Spotify are logged, shortened, with anything token-like masked.

CI checks that a fake OAuth code, the PKCE challenge and the session ID never appear in the log.

## Storage and security

An H2 database file (`./data/` locally, the `reblender-data` volume in Docker) holds:

- `oauth2_authorized_client`: Spotify access and refresh tokens, so the app can read other members' top tracks when they're not logged in.
- `app_user`: everyone who has logged in (Spotify ID, display name, profile picture URL and whether they allow being included in blends).
- `blend` / `blend_member`: each managed playlist, its settings and who's in it.

How the tokens are protected:

- **Encrypted at rest** with AES-256-GCM (`TokenCipher`). Each ciphertext is bound to its row (Spotify user ID), so a token copied into another row won't decrypt, and tampering is detected. The key is never stored in the database (see *Where the key lives* below). The app refuses to start without a valid key.
- **Fails closed.** If Spring's storage format ever changes so a token can't be encrypted, the write is refused rather than stored in plaintext. A row that can't be decrypted (wrong key, tampering, or pre-encryption data) is deleted and that user is asked to log in again.
- **Least privilege.** Only `user-top-read`, `playlist-modify-private` and `playlist-modify-public` are requested. The last one is needed only to delete a playlist: Spotify's `DELETE /me/library` refuses with "Insufficient client scope" without it. The app never edits a playlist it didn't create.
- **Never sent to the browser.** Tokens stay server-side; the browser only gets an HttpOnly, SameSite=Lax session cookie. It's marked Secure automatically when `APP_BASE_URL` is `https://` (force it either way with `SESSION_COOKIE_SECURE`).
- **Container hardening.** It runs as a non-root user, with a read-only root filesystem and `no-new-privileges`. `/data` is only readable by the app user, and the port is bound to `127.0.0.1` by default.
- **Short-lived anyway.** Access tokens last an hour; Spotify refresh tokens expire 6 months after login.
- **Login uses PKCE** (S256) on top of the client secret.
- **Browser hardening.** A Content-Security-Policy allows scripts and styles only from the app itself and images only from the app and Spotify's CDNs; links and images from Spotify are only used if they're `https://`. Pages can't be framed, and a strict Referrer-Policy and Permissions-Policy are set.
- **Behind a reverse proxy.** `X-Forwarded-*` headers are ignored unless you set `FORWARD_HEADERS_STRATEGY=native`. Only do that when the proxy sets those headers itself and strips any the client sent.
- **Opting out.** Everyone has a "Let others include me in blends" switch (on by default). Turning it off hides you from the partner list and stops any blend that includes you from being created or updated, so the app won't read your top tracks for someone else.
- **Ended sessions.** If your stored login is deleted (for example "Forget me" in another browser), any other session you had is logged out on its next request.
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

Kotlin 2.3, Spring Boot 4.1 (Spring Security OAuth2 client, MVC, Spring Data JDBC), Thymeleaf, H2, kotlinx.serialization, Docker. KtLint runs as part of `./gradlew build`.

### Layout

The code is packaged by feature:

| Package | What's in it |
|---|---|
| `blend` | The blend engine and its models. Plain Kotlin, no Spring. |
| `playlists` | `PlaylistService` and playlist naming. `data/`: Spring Data JDBC repository. `web/`: pages, view models and wording. `api/`: JSON API. |
| `accounts` | Users, login handling, the blending opt-out and "Forget me". Same `data/`, `web/` and `api/` split. |
| `integrations/spotify` | The connection to Spotify: Web API client, token access and JSON DTOs. Anything else the app talks to goes under `integrations/` too. |
| `platform` | Cross-cutting security (headers, PKCE, token encryption), logging, config and data support. |

### JSON API

The pages and the API sit on the same services. The API uses the same session login and CSRF protection as the pages. A signed-out request gets `401`, and errors come back as problem details.

| Method | Path | |
|---|---|---|
| `GET` | `/api/v1/me` | Your profile and blending setting |
| `PUT` | `/api/v1/me/blending` | `{"allowBlending": false}` |
| `DELETE` | `/api/v1/me` | Forget me |
| `GET` | `/api/v1/people` | People you can blend with |
| `GET` | `/api/v1/playlists` | Your blends |
| `POST` | `/api/v1/playlists` | `{"partnerIds": [...], "timeRange": "RECENT", "size": 40, "sharedMode": "CAPPED"}` |
| `GET` | `/api/v1/playlists/{id}` | A blend with its members and songs |
| `POST` | `/api/v1/playlists/{id}/refresh` | Rebuild from current top tracks |
| `PUT` | `/api/v1/playlists/{id}/auto-update` | `{"autoUpdate": false}` turns the weekly update off |
| `DELETE` | `/api/v1/playlists/{id}?removeFromSpotify=true` | Delete |
