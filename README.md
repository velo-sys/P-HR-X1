# P-HR-X1 · Y2K Music Player

A lightweight, offline music player for Android handhelds with a gamepad, styled like a 2000s hi-fi car deck: brushed aluminium, a glowing VFD display, a pixel-art folder drum and 24 pixel-art visualizers that react to the real audio signal.

Built for 4:3 screens (designed and tested at 960×720 on Android 12) and controlled entirely with the D-pad and face buttons — touch works too.

| Boot | Setup | Visualizer preview | Night Drive |
|---|---|---|---|
| ![Boot](docs/screenshots/boot.png) | ![Setup](docs/screenshots/setup.png) | ![Visualizer](docs/screenshots/setup-visualizer.png) | ![Night Drive](docs/screenshots/night-drive.png) |

**Interactive preview:** open [`docs/index.html`](docs/index.html) in a browser (simulated music, keyboard or click controls).

## Features

- **Folders are playlists** – every folder with music becomes a slot on the pixel drum (▲▼). Add any folder manually through a bash-style browser.
- **Bash-style folder list** (B) – `ls` without permissions or dates, `cd "…"` to play.
- **VFD main display** – format badge (MP3 / FLAC / WAV · bit depth · sample rate or bitrate · HI-RES), pixel status icons (play, pause, seek, shuffle, repeat, sleep), marquee in 4 styles.
- **24 visualizers**, small and full screen in sync (L / R to switch, SELECT for full screen): bars, mirror, dot matrix, oscilloscope, waterfall, VU needles, LED meter, pixel flames, warp, synth skyline, Lissajous, dolphins, plasma, tunnel, galaxy, lava lamp, orbit, cells, kaleidoscope, DNA helix, pixel rain, polyhedron, jellyfish and **Night Drive**. They read the PCM stream directly from the player (no microphone permission) and get more intense with volume (full power from 50 %).
- **Two-color rule** – every color scheme is exactly a main and a second color plus lighter/darker shades. 25 colors, rainbow mode, presets like *Lavender / Red*, optional swap on the beat.
- **Setup window** with its own display and live previews; on/off options are knobs, multi-value options are rollers.
- **English (default) and German**, switchable under *System › Language*.
- Background playback with notification and headset buttons, sleep timer, fade in/out, resume position, about screen in neofetch style.
- No internet permission, no ads, no tracking, no accounts. APK ≈ 1.2 MB.

## Controls

| Button | Action |
|---|---|
| A | Play / pause |
| ◀ ▶ | Previous / next track · hold = seek |
| ▲ ▼ | Previous / next folder (drum) |
| B | Folder list (bash style) |
| START | Setup |
| SELECT | Full-screen visualizer on/off |
| L / R | Switch visualizer |
| X / Y | Shuffle / repeat |
| VOL ± | Volume with on-screen display |

If A/B are swapped on your device, use *System › Swap A/B*. *System › Button test* shows the raw key codes.

## Formats

All audio files are always listed. Playable now: **MP3, FLAC, WAV, M4A/AAC, OGG, Opus**. AIFF, ALAC, DSD, WavPack, APE and WMA need an extra decoder (planned) and are skipped until then.

## Build

Requirements: `podman` (or Docker with the same arguments) and the Android SDK command-line tools.

```sh
./build.sh            # builds app/build/outputs/apk/release/app-release.apk and copies it to P-HR-X1.apk
```

`build.sh` runs Gradle 8.10 / JDK 17 in a container. It expects the SDK in `../.android-sdk` and a Gradle cache in `../.gradle-home` (override with `ANDROID_SDK` / `GRADLE_CACHE`).

**Signing:** create `keystore.properties` (not part of the repo) to sign with your own key, otherwise the debug key is used:

```properties
storeFile=keystore/your-key.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Install with `adb install -r P-HR-X1.apk` or copy the APK to the device.

## Project layout

| File | Purpose |
|---|---|
| `Engine.kt` | Media3/ExoPlayer, folder queue, options → player, format detection, state |
| `Analyzer.kt` | FFT, 32 bands, waveform, L/R level and beat detection from the PCM stream |
| `Library.kt` | MediaStore scan, folders as playlists, directory tree for the folder browser |
| `DeckView.kt` | The whole UI (custom canvas, 440×330 logical) and input handling |
| `Viz.kt` · `Drum.kt` · `Pix.kt` | Visualizers, pixel drum, pixel buffer and 5×7 font |
| `Opts.kt` · `SysInfo.kt` · `Icons.kt` | Options/menus/colors (EN + DE), neofetch data, pixel icons |

## Disclaimer

P-HR-X1 is an independent hobby project by velo-sys. It is not affiliated with, endorsed by or sponsored by any device manufacturer or any audio certification body. The on-screen print uses the project's own name; "Hi-Fi" and "HI-RES" are used only as generic descriptions, not as logos. All other product names and trademarks belong to their respective owners and are mentioned only to describe compatibility.

---

## Kurz auf Deutsch

Leichter Offline-Musikplayer für Android-Handhelds mit Gamepad im Stil eines Hi-Fi-Autoradios der 2000er: Alu-Optik, VFD-Anzeige, Pixel-Walze für die Ordner und 24 Pixel-Visualizer, die auf das echte Signal reagieren. Jeder Ordner ist eine Playlist, die Bedienung läuft über die Tasten (Touch geht auch), Sprache Englisch (Standard) oder Deutsch. Bauen mit `./build.sh`, Vorschau im Browser unter `docs/index.html`.
