# Space Bachao

Offline Android app that compresses photos, videos and scanned PDFs on the phone to free up storage.
No INTERNET permission: files never leave the phone.

| Type | How | Typical saving |
|---|---|---|
| Photos (JPEG) | Same resolution, re-encoded; date, location, camera info and time-zone kept | 30–60% |
| Videos (MP4) | H.265 via the phone's hardware encoder (Media3 Transformer); date/location written back | 40–55% |
| Scanned PDFs | Pages re-rendered as clean JPEG pages | 50–80% |

- **Replace** mode: smaller file takes the original's place, original kept in the Bin (7/15/30 days), one-tap restore. Original file dates are put back so the gallery order doesn't change.
- **Keep both** mode: smaller copy in `Pictures/SpaceBachao`, `Movies/SpaceBachao` or `Download/SpaceBachao`.
- **Free:** unlimited photos, 3 videos + 2 PDFs per day. **Pro** (one-time, Google Play Billing 8, product id `pro_lifetime`): unlimited, all quality modes, longer Bin.

## Builds
GitHub Actions (`.github/workflows/build.yml`) builds on every push to `space-bachao`:
- `SpaceBachao.apk` – **preview** build (minified like release, signed with the checked-in test key). For testing on your phone. Published as a GitHub Release.
- `SpaceBachao-unsigned.aab` – Play Store bundle, signed afterwards with the private upload key (`jarsigner`).

Local build: open in Android Studio, or `./gradlew assemblePreview bundleRelease`.
To sign the bundle directly, set `SB_KEYSTORE_PATH`, `SB_KEYSTORE_PASSWORD`, `SB_KEY_ALIAS`, `SB_KEY_PASSWORD`.

Targets Android 16 (API 36), min Android 10 (API 29).

## Play Store
See `PLAY_STORE.md` (listing text, data safety, photo/video permission declaration, Pro product, release steps) and `PRIVACY.md`.
Store graphics are in `playstore/`.
