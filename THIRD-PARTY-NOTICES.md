# Third-party notices

The app's own source code is under the MIT license, see [LICENSE](LICENSE). The parts listed here are
**not** covered by it – each keeps its own license.

## Shipped inside the APK

Every runtime dependency is under the **Apache License 2.0**:

- AndroidX Media3: `media3-exoplayer`, `media3-session` and their transitive modules
  (`media3-common`, `media3-decoder`, `media3-extractor`, `media3-datasource`)
- AndroidX support libraries pulled in by Media3 (e.g. `annotation`, `core`)
- Guava (used by `media3-session`)
- Kotlin standard library

## Artwork

The Android robot (drawn as a hat on the app icon in the About screen) is reproduced or modified from
work created and shared by Google and used according to terms described in the Creative Commons 3.0
Attribution License.

## Preview page

The app itself ships no fonts, it draws with the fonts of the device. So that the browser preview
`docs/index.html` looks the same as the app, it embeds subsets of the two Android system fonts the app
draws with, both under the **Apache License 2.0**:

- Roboto – copyright Google, https://github.com/googlefonts/roboto
- Droid Sans Mono – copyright Google, https://android.googlesource.com/platform/frameworks/base/+/master/data/fonts/
