# LUT Cam

Android camera app that colour-grades the live preview in real time with 3D LUTs (GPU / OpenGL ES).

## Features
- 16 built-in looks: Cinematic, Teal & Orange, Golden Hour, Moody, Desi Wedding, Vintage, Film Warm, Film Green, Cyberpunk, Cold Winter, Pastel, Vivid, Bleach Bypass, Noir, Sepia + Original
- Import your own `.cube` LUTs (any size, auto-resampled to 32³); long-press an imported LUT to delete it
- **AI Auto**: measures every few frames and smoothly fixes exposure and white balance before the LUT is applied
- Intensity slider, swipe left/right to change look, hold on the preview to compare with the original
- Photos saved to `Pictures/LUTCam`, with the grade baked in

## Build
Every push to `main` builds the APK with GitHub Actions and publishes it under **Releases**.
Locally: `./gradlew assembleRelease` (needs the Android SDK).
