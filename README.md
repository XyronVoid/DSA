# Dynamic Island for Android

iPhone jaisa Dynamic Island, Android phone ke liye.

## Kya kya karta hai
- **Idle**: front camera ke upar chhota black circle
- **Messages**: WhatsApp / Insta / SMS aaye to island bada hoke **poora message** dikhata hai (photo + naam + text). Tap karo to wo chat khul jayegi
- **Music**: gaana chale to lamba pill: album art + naachti bars (album ke colour me). Tap karo to bada player: play/pause, next, previous
- **Charging**: charger lagao to "Charging 76%" green battery ke saath
- **Long press** circle pe = battery % dikhata hai
- **Upar swipe** = band, **neeche swipe** (music pill pe) = khul jayega
- Landscape (video / game) me apne aap chhup jata hai
- Springy iPhone jaisa animation

## APK kaise banaye

### Tarika 1: Android Studio (laptop pe)
1. Android Studio install karo: https://developer.android.com/studio
2. Ye zip extract karo, phir **File → Open** → `DynamicIsland` folder choose karo
3. Pehli baar Gradle sync hoga (5-10 min, internet chahiye). Neeche progress dikhega
4. Upar menu: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**
5. Ban jaane pe popup me **locate** dabao. File: `app/build/outputs/apk/debug/app-debug.apk`
6. Ye APK phone pe bhejo aur install karo

Ya phone USB se jodo (USB debugging ON), aur upar green ▶ Run button dabao. Seedha phone pe install ho jayega.

### Tarika 2: GitHub se (Android Studio ki zarurat nahi)
1. GitHub pe naya repository banao, saari files upload karo (`.github` folder bhi)
2. **Actions** tab kholo. "Build APK" apne aap chalega (~5 min)
3. Complete hone pe run kholo, neeche **Artifacts** me `DynamicIsland-APK` download karo

## Phone pe setup
1. App kholo
2. **Accessibility ON karo** → list me "Dynamic Island" → ON
3. **Notification access do** → "Dynamic Island" → Allow
4. Agar setting grey / "Restricted setting" bole: App info kholo → upar right **⋮** → **Allow restricted settings** → phir dubara ON karo (Android 13+ me sideloaded apps ke saath hota hai)
5. Sliders se circle ko camera ke upar fit karo
6. **Test message** dabao

### Xiaomi / Redmi / Realme / Oppo / Vivo walon ke liye
Ye phones background apps ko kill kar dete hain. Island gayab ho jaye to:
- App info → Battery → **No restrictions / Unrestricted**
- **Autostart** ON karo
- Recent apps me app ko lock kar do

## Files
- `IslandController.kt`: island ka dimaag (animation, modes, layouts)
- `IslandService.kt`: island ko status bar ke upar draw karta hai
- `NotifListener.kt`: messages + music padhta hai
- `MainActivity.kt`: settings screen
- `Views.kt`: play/pause icons, music bars, battery icon, spring animation
