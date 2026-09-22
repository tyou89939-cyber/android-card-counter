# Card Counter for Android

A native Java Android app that captures a user-approved screen, uses OpenCV
template matching to identify visible card suit glyphs, decrements each suit
from 13, and renders the remaining counts in a slim floating top bar.

## Requirements

- Android Studio Hedgehog or newer
- JDK 17
- Android SDK Platform 35 and Build Tools
- A physical Android device or emulator running Android 8.0 (API 26) or newer

The project uses the OpenCV Android Maven artifact, so no separate OpenCV SDK
download is needed.

## Build locally

From the repository root:

```bash
cd android-card-counter
gradle assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Open `android-card-counter` in Android Studio if you prefer the Gradle UI.

## Use the app

1. Install the debug APK.
2. Open Card Counter and choose **Allow floating overlay**.
3. Choose **Start screen capture** and approve Android’s capture prompt.
4. Keep the suit symbol for the played card visible in the captured game.
5. The bar at the top shows the remaining Hearts, Diamonds, Spades, and Clubs.
6. Use **Reset to 13 each** before a new deck.

Android intentionally shows a persistent foreground notification while capture
is active. Stopping the capture removes the overlay and releases the virtual
display.

## Calibrate detection for a target game

The detector is deliberately template-based because every card game renders
its suit glyphs, scale, anti-aliasing, and placement differently. Capture a
tightly cropped grayscale glyph from the target app and replace:

```text
app/src/main/assets/templates/hearts.pgm
app/src/main/assets/templates/diamonds.pgm
app/src/main/assets/templates/spades.pgm
app/src/main/assets/templates/clubs.pgm
```

The files can be PNG/JPEG instead if you update the filename in
`TemplateCardDetector.java`. The detector requires two consistent frames and a
short cooldown before decrementing, which prevents a single visible card from
being counted every frame.

This starter implementation matches the glyph anywhere on the captured frame.
For production use against a busy screen, narrow the detector to the known card
region before matching.

## Connect this workspace to GitHub

1. Create an empty repository on GitHub. Do not add a README or `.gitignore`
   there because this workspace already contains them.
2. In this Replit shell, from the repository root, configure your identity if
   Git asks for it:

   ```bash
   git config user.name "Your Name"
   git config user.email "you@example.com"
   ```

3. Initialize and commit the project:

   ```bash
   git init
   git add .
   git commit -m "Initial Android card counter"
   ```

4. Copy the repository URL from GitHub and add it as the remote:

   ```bash
   git remote add origin https://github.com/YOUR_USER/YOUR_REPO.git
   git branch -M main
   git push -u origin main
   ```

   If GitHub requests a password over HTTPS, use a GitHub personal access
   token or authenticate with GitHub CLI. Never commit credentials to this
   repository.

## Download the APK from GitHub Actions

The workflow at `.github/workflows/build.yml` runs on every push and pull
request. It installs JDK 17 and Gradle 8.7, builds `assembleDebug`, and uploads
the APK as an artifact.

1. Open the GitHub repository.
2. Select the **Actions** tab.
3. Open the latest **Build Android debug APK** run.
4. Wait for the green check.
5. Under **Artifacts**, download `card-counter-debug-apk`.
6. Unzip the downloaded artifact to get `app-debug.apk`.

## Privacy and platform behavior

Capture is only available after Android’s system consent prompt. Frames are
processed in memory by the foreground service and are not uploaded anywhere by
this project. The overlay requires `SYSTEM_ALERT_WINDOW`, and Android may show
additional confirmation screens depending on device manufacturer.