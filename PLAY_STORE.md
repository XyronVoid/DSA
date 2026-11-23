# Space Bachao – Play Store launch kit

Everything you need to copy-paste into Play Console.

---

## 1. Upload file
- **File:** `SpaceBachao-1.1.0-signed.aab` (signed with your upload key)
- When Play Console asks about app signing, choose **"Use Google-generated key" (Play App Signing)**. Your `spacebachao-upload.jks` stays as the *upload* key.
- **Package name:** `com.spacebachao.app` (can never change)
- **Version:** 1.1.0 (versionCode 2). Every new upload needs a higher versionCode.

---

## 2. Store listing

**App name (max 30):**
Space Bachao: Photo Compressor

**Short description (max 80):**
Free up phone space. Shrink photos & videos offline, quality looks the same.

**Full description:**
Phone full again? Space Bachao shrinks your photos, videos and scanned PDFs right on your phone, so you get gigabytes back without deleting a single memory.

★ 100% OFFLINE & PRIVATE
Space Bachao has no internet permission at all. Your photos and videos never leave your phone. No uploads, no cloud, no ads, no tracking.

★ SAME QUALITY, LESS SPACE
• Photos: 30–60% smaller at the same resolution
• Videos: 40–55% smaller with modern H.265 compression, using your phone's hardware encoder
• Scanned PDFs: up to 80% smaller

★ YOUR GALLERY STAYS IN ORDER
Dates, locations and camera details are kept, so your photos stay in the same place in your gallery.

★ SAFE BY DESIGN
• Originals are kept in the Bin for 7 days, restore any file with one tap
• Files that wouldn't save much are left untouched
• Motion photos and portrait photos are skipped so their effects never break
• Nothing is compressed twice

★ BIGGEST FILES FIRST
See exactly which photos and videos eat your space, select them and tap Compress. Track how many are done and how many are left.

FREE: unlimited photos, 3 videos and 2 PDFs per day.
PRO (one-time payment, no subscription): unlimited videos and PDFs, High quality and Max saving modes, longer Bin, all future features.

**Category:** Tools
**Tags:** Storage, File management, Photo compressor
**Contact email:** your email
**Privacy policy URL:**
https://github.com/XyronVoid/DSA/blob/space-bachao/PRIVACY.md

**Graphics:**
- App icon 512×512: `play-icon-512.png`
- Feature graphic 1024×500: `play-feature-1024x500.png`
- Phone screenshots: at least 2 (take them from your phone: Home, Compressing, Result, Pro)

---

## 3. In-app product (Pro)
Play Console → **Monetize with Play → Products → One-time products** → Create
- **Product ID:** `pro_lifetime` (must match exactly)
- **Name:** Space Bachao Pro
- **Description:** Unlimited videos & PDFs, all quality modes, longer Bin. One-time payment.
- **Price:** suggestion ₹149 (launch offer ₹99)
- Activate it.

> You need a **payments profile** (Merchant account) set up in Play Console to sell. Products can only be created after you upload the first AAB to any testing track.

---

## 4. App content (Policy) answers

**Privacy policy:** the URL above.

**Ads:** No, the app does not contain ads.

**App access:** All functionality is available without login. (Pro can be tested with a license tester account.)

**Content rating questionnaire:** Category "Utility, Productivity, Communication, or Other". Answer **No** to everything (no violence, no user content, no gambling...). Result should be "Everyone / 3+".

**Target audience:** 18+ (or 13+). Not designed for children.

**Data safety:**
- Does your app collect or share any of the required user data types? → **No**
- Reason: all processing is on-device and the app has no internet permission. (Purchases are processed by Google Play, which Google covers.)

**Photo and Video Permissions declaration** (READ_MEDIA_IMAGES / READ_MEDIA_VIDEO):
- Core use case: **Device storage management / compression of the user's photos and videos**
- Text to paste:

> Space Bachao is a storage-saving app. Its core and only function is to scan the user's photo and video library, show the largest files, and compress them in place to free storage space, keeping the original in an in-app Bin for restore. This requires persistent, broad access to all photos and videos on the device: the app needs to list and sort the whole library by size, read each file to re-encode it, and write the smaller version back to the same file (with the user's confirmation through MediaStore.createWriteRequest). The Android Photo Picker is not sufficient because it gives temporary, read-only copies of user-selected items: the app could neither find the largest files across the library nor replace the original files to actually free up space. All processing happens on-device; the app has no INTERNET permission and no data leaves the device.

- Attach a short screen recording (30–60 s): open app → Photos list sorted by size → select → Compress → Result → gallery shows same photo.

**Location permission (ACCESS_MEDIA_LOCATION):** used only to preserve the existing location metadata inside photos/videos during compression. No location is collected.

---

## 5. Release steps (in order)
1. Play Console → Create app → name, language English, App, Free → accept.
2. Fill **App content** (section 4) and **Store listing** (section 2).
3. **Testing → Internal testing** → Create release → upload the signed AAB → add your own email as tester → roll out.
4. Install from the internal test link on your phone. Test compressing + Pro purchase (add yourself under *License testing* so purchases are free).
5. New personal developer accounts must run a **Closed test with at least 12 testers for 14 days** before production. Use friends/family; ask them to open the app a few times.
6. Then **Production → Create release** → same AAB (or a newer one) → submit for review.

---

## 6. Every future update
1. Increase `versionCode` (and `versionName`) in `app/build.gradle.kts`.
2. Build the release bundle and sign it with **the same** `spacebachao-upload.jks`.
3. Upload to Play Console.
