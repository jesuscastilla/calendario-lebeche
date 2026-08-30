# Fix App Crash and Logo Issues

The user reports that the app closes immediately on launch and the icon is cropped and not centered. This plan addresses both issues by improving stability and correctly defining the adaptive icon.

## User Review Required

> [!IMPORTANT]
> The target SDK will be lowered from 36 (Android 16 preview) to 35 (Android 15) to ensure better compatibility with current devices and libraries, as SDK 36 is still in preview.

## Proposed Changes

### Build Configuration

#### [MODIFY] [app/build.gradle](file:///G:/GITHUB/calendario-lebeche/app/build.gradle)
- Lower `compileSdk` and `targetSdk` to 35 for better stability.
- Ensure all dependencies are compatible.

---

### App Icon

The current icon is cropped because the foreground PNG doesn't have enough padding for the adaptive icon "safe zone".

#### [NEW] [ic_launcher_foreground.xml](file:///G:/GITHUB/calendario-lebeche/app/src/main/res/drawable/ic_launcher_foreground.xml)
- Create a Vector Drawable for the flame logo. This will ensure it's perfectly centered and scales without losing quality.
- Alternatively, if a vector cannot be perfectly matched, use an `<inset>` wrapper for the existing PNG to provide the required 1/6th margin.

#### [MODIFY] [ic_launcher.xml](file:///G:/GITHUB/calendario-lebeche/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml)
- Reference the new centered foreground drawable.

#### [MODIFY] [ic_launcher_round.xml](file:///G:/GITHUB/calendario-lebeche/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml)
- Reference the new centered foreground drawable.

---

### Stability Fixes

#### [MODIFY] [App.kt](file:///G:/GITHUB/calendario-lebeche/app/src/main/java/com/lebeche/calendario/App.kt)
- Wrap initialization in a try-catch block to prevent immediate crashes during startup (e.g., if WorkManager fails to initialize).

#### [MODIFY] [MainActivity.kt](file:///G:/GITHUB/calendario-lebeche/app/src/main/java/com/lebeche/calendario/MainActivity.kt)
- Ensure Compose content is set safely and handle potential theme issues.

## Verification Plan

### Automated Tests
- Run `:app:assembleDebug` to ensure the project still builds.
- Run the app on an emulator (API 34/35) to verify it no longer crashes.

### Manual Verification
- Check the app icon in the launcher to ensure it's centered and not cropped.
- Verify that the splash screen (if any) and transition to main screen are smooth.
