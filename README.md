# P-HR-X1

<div align="center">

## 🤖 Vibecoded with Claude Code

[![Built with Claude Code](https://img.shields.io/badge/built%20with-Claude%20Code-D97757?style=for-the-badge)](https://claude.com/claude-code)

**This entire app was written by [Claude Code](https://claude.com/claude-code)** — the Kotlin, the canvas
interface, the FFT analyzer, all 24 pixel visualizers and the interactive preview page — round by round,
from HTML design proposals to the signed APK.

</div>

---

A music player in 2000s hi-fi style for Android gamepad handhelds – **fully offline**, no account, no
server, no `INTERNET` permission in the manifest. Every folder with music becomes a playlist, and the
whole app is controlled with the D-pad and face buttons.

[Download the APK](https://github.com/velo-sys/P-HR-X1/releases/latest) · [Interactive preview](docs/index.html)

## Screens

| Main screen | Folder list (B) |
|---|---|
| ![Main screen](docs/screenshots/main.png) | ![Folder list](docs/screenshots/folders.png) |
| VFD display, small visualizer and the folder drum | Bash-style list: track count and folder name |

| Setup (START) | Visualizer setup |
|---|---|
| ![Setup](docs/screenshots/setup.png) | ![Visualizer setup](docs/screenshots/setup-visualizer.png) |
| Seven categories, the display shows the selection | Live preview of the small and the full-screen visualizer |

| Full-screen visualizer (SELECT) | Boot animation |
|---|---|
| ![Night Drive](docs/screenshots/night-drive.png) | ![Boot](docs/screenshots/boot.png) |
| Night Drive, one of 24 visualizers | VFD test on start |

All screenshots: color scheme Lavender / Red, brightness 100 %.

## Features

**24 visualizers**, driven by the real audio signal, small and full screen in sync:

| Group | Visualizers |
|---|---|
| Classic | Bars · Mirror · Dot Matrix · Oscilloscope · Waterfall · VU Needles · LED Meter |
| Retro | Pixel Flames · Warp · Synth-Skyline · Night Drive · Lissajous |
| Abstract | Dolphins · Plasma · Tunnel · Galaxy · Lava Lamp · Orbit · Cells · Kaleidoscope · DNA Helix · Pixel Rain · Polyhedron · Jellyfish |

They read the PCM stream straight from the player (no microphone permission), react to the beat and get
more intense with the volume, at full power from 50 %.

**On top of that:**

- **Folders are playlists** on a pixel-art drum (▲▼); any folder can be added through a bash-style browser
- **Bash-style folder list** (B): `ls` with track count and folder name, `cd "…"` to play
- **VFD display** with format, bit depth, sample rate or bitrate and pixel status icons for play, pause,
  seek, shuffle, repeat and sleep
- **Two-color schemes**: a main and a second color in lighter and darker shades, 25 colors, rainbow mode,
  presets such as Lavender / Red, optional swap on the beat
- **Setup window** with its own display and live previews; on/off options are knobs, everything else rollers
- **Formats**: MP3, FLAC, WAV, M4A/AAC, OGG and Opus; all audio files are always found
- English and German, touch control, background playback with notification and headset buttons,
  sleep timer, fade in/out, resume position, about screen with device info

## Controls

| Button | Action |
|---|---|
| A | Play / pause |
| ◀ ▶ | Previous / next track · hold = seek |
| ▲ ▼ | Previous / next folder |
| B | Folder list |
| START | Setup |
| SELECT | Full-screen visualizer |
| L / R | Switch visualizer |
| X / Y | Shuffle / repeat |
| VOL ± | Volume |

If A and B are swapped on your device, use *System › Swap A/B*; *System › Button test* shows the raw key codes.

## Setup menu

| Category | Options |
|---|---|
| Playback | Shuffle · Repeat · At folder end · Sleep timer · Fade in/out · Gapless · Autoplay on start · Pause on unplug |
| Library | Music folder · Add folder · Subfolders separate · Track order · Hidden folders · Rescan |
| Visualizer | Visualizer · Smoothing · Follows volume · Info line (full screen) · Show full screen |
| Colors | Color scheme · Main color · Second color · Two-tone style · Peak color |
| Display | Brightness · Glow · Scanlines · Flicker · Format display · Keep screen on |
| Motion | Marquee shows · Marquee style · Marquee speed · Drum · Window transition · Boot animation · Show boot animation |
| System | Language · Touch control · Swap A/B · Volume display · Button map · Button test · About · Factory reset · Quit |

## Install

A signed APK is attached to each [release](../../releases). Download it, open it on the device and allow
installation from unknown sources once, or run `adb install -r`. Updating over an existing install keeps
all settings, as long as the APK comes from the same release line.

Android 8.0 or newer, designed for 4:3 screens (960×720). On other screens the picture keeps its 4:3 aspect ratio with black bars, it is never stretched. Grant "Music and audio" access on first start.

## Build

Requirements: `podman` (or Docker with the same arguments) and the Android SDK command-line tools.

```bash
git clone https://github.com/velo-sys/P-HR-X1
cd P-HR-X1
./build.sh                       # APK: P-HR-X1.apk
```

`build.sh` runs Gradle 8.10 / JDK 17 in a container and expects the Android SDK in `../.android-sdk`
(override with `ANDROID_SDK`) and a Gradle cache in `../.gradle-home` (override with `GRADLE_CACHE`).

### Signing a release (optional)

Create a `keystore.properties` in the project root (it is listed in `.gitignore`):

```properties
storeFile=keystore/your-key.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Without that file the release build is signed with the debug key.

### Your own application ID

If you publish the app under your own name, change `namespace` and `applicationId` in
`app/build.gradle.kts` (currently `de.y2k.player`) and rename the package directory under
`app/src/main/java/` to match.

## Under the hood

Kotlin · AndroidX Media3 (ExoPlayer, MediaSession) · custom canvas interface without Compose ·
hand-written FFT and beat detection · MediaStore · minSdk 26 · APK about 1.2 MB

```
app/src/main/java/de/y2k/player/
  Engine.kt      playback, folder queue, options → player, format detection
  Analyzer.kt    FFT, 32 bands, waveform, levels, beat detection from the PCM stream
  Library.kt     MediaStore scan, folders as playlists, folder tree for the browser
  DeckView.kt    the whole interface (440×330 logical, scaled) and input handling
  Viz.kt         the 24 visualizers
  Drum.kt        pixel folder drum
  Pix.kt         pixel buffer, 5×7 font, colors
  Opts.kt        options, menus, colors, English and German texts
  SysInfo.kt     device info for the about screen
docs/            interactive preview page and screenshots
```

## Contributing

Pull requests are welcome. Two requests:

- `./build.sh` has to pass.
- Interface text goes through `L("Deutsch", "English")` in both languages.

## License

MIT, see [LICENSE](LICENSE).

The APK ships Apache-2.0 dependencies, and the Android robot is used under CC BY 3.0. What belongs to
whom is listed in **[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)**.

P-HR-X1 is an independent project and not affiliated with any device manufacturer. Product names belong
to their respective owners.
