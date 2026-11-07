# spotify-reblender

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
4. `cp .env.example .env` and fill in the client ID and secret (and `APP_BASE_URL` if not local).

## Run

With Docker:

```sh
docker compose up --build
```

Without Docker (JDK 21):

```sh
export SPOTIFY_CLIENT_ID=... SPOTIFY_CLIENT_SECRET=...
./gradlew bootRun
```

Open <http://127.0.0.1:8080>, log in, then have your partner do the same (on their device or after you log out). Pick them from the list, choose the range, size and shared mode, and create the playlist. Use **Update** to rebuild it with current top tracks.

Tests: `./gradlew test` (covers the blend engine).

## Storage

An H2 database file (`./data/` locally, the `reblender-data` volume in Docker) holds:

- `oauth2_authorized_client`: Spotify tokens (so the app can read your partner's top tracks when they're not logged in). **Stored unencrypted**, so keep the volume private.
- `app_user`: everyone who has logged in.
- `pairing`: each managed playlist and its settings.

## Stack

Kotlin 2.3, Spring Boot 4.1 (Spring Security OAuth2 client, MVC, JDBC), Thymeleaf, H2, kotlinx.serialization, Docker.
