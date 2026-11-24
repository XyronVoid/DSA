# ReelRok 🛑

Regain jaisa app — YouTube Shorts aur Instagram Reels khulte hi band kar deta hai.

## Kya karta hai
- Shorts / Reels player dikha → turant **Back** + 1.4 sec ki "ruk ja" screen
- YouTube videos, search, Instagram feed, DMs — sab normal chalta hai
- Aaj kitni baar bacha, total kitni baar — counter
- **5 min pause** (10 sec wait ke baad hi confirm hota hai — impulse rokne ke liye)
- Switch OFF karne pe bhi 10 sec wait
- Koi internet permission nahi, koi data bahar nahi jaata

## APK kaise banaye (bina Android Studio)
1. GitHub pe naya repo banao (ya DSA repo me naya branch, jaise `reelrok`)
2. Is folder ki saari files upload karo — `.github` folder bhi (hidden hota hai, dhyan se)
3. **Actions** tab → "Build ReelRok APK" → complete hone do (~5 min)
4. Run kholo → neeche **Artifacts** me `ReelRok-apk` download → zip me APK

## Phone pe chalu karna
1. APK install karo
2. App kholo → **Blocker ON karo** → Accessibility → Installed apps → **ReelRok Blocker** → ON
3. Android 13+ pe "Restricted setting" aaye to: Settings → Apps → ReelRok → ⋮ (upar kona) → **Allow restricted settings** → phir se step 2
4. Motorola pe: Settings → Apps → ReelRok → Battery → **Unrestricted** (taaki system band na kare)

## Agar kisi update ke baad block na ho
YouTube/Instagram kabhi-kabhi view IDs badal dete hain. `BlockerService.kt` me
`SHORTS_IDS` / `REELS_IDS` list me naya ID add karna padega.
