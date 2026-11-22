# Space Bachao (v1)

Phone ki photos, videos aur scan wale PDF ko phone ke andar hi compress karke jagah banane wala Android app.
Isme **internet permission hai hi nahi**, toh koi file phone se bahar nahi ja sakti.

## Kya karta hai

| Type | Kaise | Kitna bachta hai (lagbhag) |
|---|---|---|
| Photos (JPEG) | Same resolution, dobara encode. Date, location, camera info copy hoti hai | 30–60% |
| Videos (MP4) | H.265 (HEVC) me, phone ke hardware encoder se. Resolution/FPS same | 40–55% |
| PDF (scan wale) | Har page saaf image banke naya PDF | 50–80% |

- **Replace mode:** compressed file original ki jagah aati hai, original **Bin** me 7/15/30 din safe rehta hai (1 tap me Restore)
- **Dono rakho mode:** original waisa hi, chhoti copy `Pictures/SpaceBachao`, `Movies/SpaceBachao` ya `Download/SpaceBachao` me
- Jis file me bachat kam ho (photo me 15% se kam, video me 10% se kam), use chhod deta hai aur original nahi chhedta
- Motion photos, portrait (depth) photos aur password wale PDF chhod diye jaate hain
- Ek baar compress hui file dobara list me nahi aati (double compression se quality nahi girti)

## APK kaise banega

**Option A: GitHub (phone se bhi chalega)**
1. Ye folder GitHub repo me push karo
2. Repo me **Actions** tab, phir **Build APK** kholo
3. Build poora hone pe neeche **Artifacts** me `SpaceBachao-debug-apk` download karo, zip kholo, APK install karo

**Option B: Android Studio (PC)**
1. Android Studio me `Open` karke ye folder chuno
2. Gradle sync hone do (pehli baar internet se cheezein download hongi)
3. Phone USB se lagao, ▶ Run dabao

## Pehli baar test kaise karein (zaroori)
1. Settings me Bin **7 din** rakho
2. Sirf 3–4 photos aur 1 video chuno, **Replace** mode me compress karo
3. Gallery me khol ke dekho: quality, date, location sahi hai?
4. Bin me jaake ek file **Restore** karke dekho
5. Sab theek ho tabhi badi files pe chalao

## Abhi ki limits (v2 me aayega)
- Compress ke time app khula rakhna padta hai (background service v2 me)
- Photos me sirf JPEG, videos me sirf MP4. PNG/HEIC/WhatsApp ki purani formats v2 me
- Bin apne aap tab saaf hota hai jab app khulta hai
- Play Store pe daalne se pehle: target SDK 36 karna hoga, aur Play Console me photo/video permission ka declaration form bharna hoga

## Code kahan hai
- `engine/ImageCompressor.kt`: photo compress + EXIF copy
- `engine/VideoCompressor.kt`: H.265 video (Media3 Transformer), date/location wapas MP4 me
- `engine/PdfCompressor.kt`: PDF pages ko image banake naya PDF (khud ka chhota PDF writer)
- `data/BinManager.kt`: originals ka locker, restore
- `AppViewModel.kt`: poora flow (scan, compress, safe replace, bin)
- `ui/`: screens
