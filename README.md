# Space Bachao

Offline Android app that compresses photos, videos and scanned PDFs on the phone to free up storage.
100% secure: the app has no INTERNET permission, so files never leave the phone.

| Type | How | Typical saving |
|---|---|---|
| Photos (JPEG) | Same resolution, re-encoded; date, location, camera info and time-zone kept | 30–60% |
| Videos (MP4) | H.265 via the phone's hardware encoder (Media3 Transformer); date/location written back | 40–55% |
| Scanned PDFs | Pages re-rendered as clean JPEG pages | 50–80% |

- **Replace** mode: smaller file takes the original's place, original kept in the Bin (7/15/30 days), one-tap restore. Original file dates are put back so the gallery order doesn't change.
- **Keep both** mode: smaller copy in `Pictures/SpaceBachao`, `Movies/SpaceBachao` or `Download/SpaceBachao`.
- **Free:** up to 5 GB of files (all types together). **Pro** subscription (Google Play Billing 8, product id `space_bachao_pro`, base plans `monthly`/`yearly`): unlimited, all quality modes, longer Bin.
- Test builds (`preview`, `debug`) have Pro unlocked (`BuildConfig.DEV_PRO`); the Play Store `release` build never does.

## Builds
GitHub Actions (`.github/workflows/build.yml`) builds on every push to `space-bachao`:
- `SpaceBachao.apk` – **preview** build (minified like release, signed with the checked-in test key, Pro unlocked). For the developer's phone only.
- `SpaceBachao-unsigned.aab` – Play Store bundle, signed afterwards with the private upload key (`jarsigner`).

Local build: open in Android Studio, or `./gradlew assemblePreview bundleRelease`.
To sign the bundle directly, set `SB_KEYSTORE_PATH`, `SB_KEYSTORE_PASSWORD`, `SB_KEY_ALIAS`, `SB_KEY_PASSWORD`.

Targets Android 16 (API 36), min Android 10 (API 29).

## Play Store
See `PLAY_STORE.md` (listing text, data safety, photo/video permission declaration, Pro product, release steps) and `PRIVACY.md`.
Store graphics are in `playstore/`.
