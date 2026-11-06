# Map Tooltip

A **client-side only** Fabric mod for **Minecraft Java 1.21.8**.

Pick any existing map ID (for example one from Donut SMP), choose any picture from your computer, and
that map shows your image **on your screen only**. Nobody else sees it, and the server and the world are
not changed.

## Features

- **Map ID + image**: enter a map ID and pick any PNG, JPG, GIF or BMP.
- **Automatic 128×128 conversion**: the image is centre-cropped to a square, scaled to 128×128, and
  dithered to the real Minecraft map palette so it looks like an in-game map.
- **Clipboard paste**: copy any picture and paste it in. No file browser.
- **Use held map**: hold a map and fill in its ID automatically.
- **Many replacements**: add as many as you like, each with its own map ID and name.
- **Live previews**: each card shows a pixel mosaic of the replacement, and the editor shows a 128×128
  preview before you save.
- **Add, edit, pause, delete**: toggle one replacement on or off, edit it, or delete it (two clicks, so
  you can't delete by accident). There is also a global switch to pause everything at once.
- **Safe**: original map pixels are restored when a replacement is paused or deleted.
- **Persistent**: rules are saved in `config/map-tooltip/` and survive restarts.

## Using it

1. Install the mod along with **Fabric API** (Fabric Loader 0.17.2+, Minecraft 1.21.8, Java 21).
2. In game, press **M** (rebindable under Controls → Map Tooltip) to open the menu.
3. Click **+ New**. Hold the map you want to replace and press **Use held map**, or type its ID.
4. Copy a picture (right-click an image → **Copy image**, or copy a `.png`/`.jpg` file), then press
   **Paste image from clipboard**.
5. Check the preview, then press **Save replacement**.

Map IDs appear on the map item's name or in the map's data, depending on the server. On Donut SMP,
you can read the ID from the map item when you hold it.

## Building it yourself

You need **JDK 21** and an internet connection the first time.

```bash
# First time only: generate the Gradle wrapper (gradlew + gradle-wrapper.jar)
gradle wrapper --gradle-version 8.14

# Build the jar
./gradlew build          # Windows: gradlew.bat build
```

The finished jar is in `build/libs/`. Put it in your `mods/` folder together with Fabric API.

To test without building the jar, run `./gradlew runClient`.

Check `gradle.properties` if a version is rejected. The Fabric version pages list the current numbers:
<https://fabricmc.net/develop/> (Fabric Loader, Fabric API and Yarn mappings for 1.21.8).

## Publishing to GitHub

```bash
git init
git add .
git commit -m "Initial commit: Map Tooltip mod"
git branch -M main
git remote add origin https://github.com/<you>/map-tooltip.git
git push -u origin main
```

The included `.github/workflows/build.yml` builds the jar on every push. Download it from the
**Actions** tab, under the run's artifacts.

## Project layout

```
src/main/java/dev/maptooltip/client/
  MapTooltipClient.java        entrypoint, M keybind
  MapTooltipScreen.java        main menu (list, toggles, delete, global switch)
  AddReplacementScreen.java    add / edit screen with clipboard paste and held-map fill
  ImageConverter.java          crop, scale to 128x128, dither to map palette
  ReplacementManager.java      saves/loads rules, applies them to maps
  Replacement.java             one rule (map ID -> image)
  Ui.java                      shared drawing helpers
  mixin/MapRendererMixin.java  hooks map drawing to apply replacements
src/main/resources/
  fabric.mod.json              mod metadata (name "Map Tooltip")
  map-tooltip.mixins.json      mixin config
  assets/map-tooltip/icon.png  mod icon
```

## Things to check after your first build

This was written without being run in a live game. These are the parts most likely to need a small
tweak:

- **Mixin target.** `MapRendererMixin` injects into `MapRenderer.draw(...)`. If the method signature
  differs in your mapping version, the mixin is skipped (`require = 0`) and the log says so. The fix is
  to update the `method = "..."` descriptor.
- **Palette.** Colours come from `MapColor.getRenderColor(byte)`. If that method name is different in
  your version, `ImageConverter` won't compile. Use the matching method in `MapColor`.
- **Clipboard.** Pasting reads the system clipboard through Java. If Paste does nothing on your system,
  copy the picture again and try once more.

## Notes

- This mod only changes what **you** see. It does not change the map on the server or for other
  players, and it does not modify the world.
- Only use it where the server rules allow client-side mods.

## Licence

MIT. See `LICENSE`.
