# Saanjh — Android music player

This is the native Kotlin Android project for **Saanjh**.

## Build the APK without installing Android Studio

You can use GitHub Actions from a phone browser. A free GitHub account is sufficient for this workflow, subject to GitHub's usage limits.

1. Sign in to GitHub and create a **new private repository** named `Saanjh`.
2. On the repository page, choose **Add file → Upload files**.
3. Extract this ZIP first if your phone's file manager supports it, then upload the *contents* of the `Saanjh_Android_Project` folder (including the hidden `.github` folder). If GitHub's mobile upload page cannot upload folders or hidden files, use GitHub's web editor to add `.github/workflows/build-apk.yml` with the contents from this project.
4. Commit the files to the `main` branch. The workflow starts automatically after the push.
5. Open the repository's **Actions** tab, select **Build Saanjh APK**, and open the latest successful run.
6. Under **Artifacts**, download `Saanjh-debug-apk`. Extract it on your phone; inside is `app-debug.apk`.
7. Tap the APK to install. Android may ask you to allow your browser or file manager to install unknown apps.

You can also start a build manually from **Actions → Build Saanjh APK → Run workflow**.

## Included features
- Dark interface with lavender accent and Saanjh branding
- Scan device audio library
- Pick a specific folder using Android's folder picker
- Song list and basic playback controls
- Shuffle and repeat

## Current limitations
This starter uses foreground Activity playback. Playback may stop when the app is closed or the system reclaims it; background playback, notification controls and lock-screen controls are not yet implemented. The GitHub workflow builds a debug APK for testing, not a Play Store release.
