# Music Player — Setup

The **Music ▶** menu opens a YouTube music player on the right side of the editor.
It uses an embedded Chromium browser (JCEF), so it needs a few extra steps the first time.

## What you need
- **JDK 17 or newer**
- The jars in `lib/jcef/` (already in the repo)
- The file `youtube-api.properties` with the API key (ask Franklin — it is **not** in the repo)

## 1. Add the API key file
Create a file named exactly `youtube-api.properties` in the **repo root**
(next to `CrescendoTextEditor.java`) with one line:

```properties
YT_API_KEY=paste-the-key-here
```

This file is in `.gitignore`. **Never commit it** and never paste the key in the group chat publicly.
The key is shared by the whole team: about 99 searches per day in total (each search or
"Load more" costs 100 of the 10,000 daily units), so don't spam the Search button.

## 2. Add the jars to your IDE
All 10 jars in `lib/jcef/` must be on the classpath.

**IntelliJ IDEA**
1. File → Project Structure → Modules → *Dependencies* tab → **+** → *JARs or Directories*
2. Select the `lib/jcef` folder → OK → Apply.

**VS Code** (Extension Pack for Java)
- In the *Java Projects* panel → *Referenced Libraries* → **+** → select all jars in `lib/jcef/`.

**NetBeans**
- Create the project *outside* the repo (Java with Ant → *Java Project with Existing Sources*,
  source folder = the repo), then Properties → Libraries → Classpath → *Add JAR/Folder* → the 10 jars.

## 3. Run configuration
- **Main class:** `CrescendoTextEditor`
- **Working directory:** the repo root (that is where it looks for `youtube-api.properties`)
- **VM options:** `--enable-native-access=ALL-UNNAMED`
  (optional — it only removes a warning)

**macOS only** — also add these VM options:
```
--add-opens java.desktop/sun.awt=ALL-UNNAMED
--add-opens java.desktop/sun.lwawt=ALL-UNNAMED
--add-opens java.desktop/sun.lwawt.macosx=ALL-UNNAMED
```

## 4. Or run it from the terminal (any IDE, Windows)
From the repo root:
```
javac -cp "lib/jcef/*" -d out CrescendoTextEditor.java music/*.java
copy music\player.html out\music\
java --enable-native-access=ALL-UNNAMED -cp "out;lib/jcef/*" CrescendoTextEditor
```
(On macOS/Linux use `cp` instead of `copy` and `:` instead of `;` in the classpath.)

## First run
The first time you open the player it downloads Chromium (~100 MB) into a `jcef-bundle/`
folder. It takes a minute and needs internet. After that it starts in a few seconds.
`jcef-bundle/` is in `.gitignore`.

## Known limitations
- **"Video unavailable" (error 150):** many official videos from big record labels block playback
  outside youtube.com. The player skips them automatically, marks them *(unavailable)*,
  and **Open in browser** plays them in your normal browser instead.
  Lo-fi, instrumental, classical and independent artists usually work fine.
- **Live streams** are filtered out: the embedded Chromium can't play their video format.

## Troubleshooting
| Problem | Fix |
|---|---|
| `youtube-api.properties (The system cannot find the file specified)` | The file is missing or not in the working directory (step 1 and 3). |
| `YouTube answered 403` | Wrong or expired key, or the daily quota is used up (resets at midnight Pacific time). |
| `player.html not found` | `music/player.html` was not copied to the output folder (step 4, second line). |
| Can't type in the search box | Click the box once more; the browser and Swing share the keyboard. |
| `NoClassDefFoundError: org/cef/...` | The jars are not on the classpath (step 2). |

## How the code is organized (`music` package)
| Class | Job |
|---|---|
| `MusicDock` | Connects the player to the editor as a sidebar (the only class the editor uses). |
| `MusicPlayerPanel` | The UI: search box, results, video, buttons. |
| `MusicService` | Interface: what any music source must do (search, play, next...). |
| `YouTubeMusicService` | `MusicService` for YouTube: queue, auto-next, skipping songs that fail. |
| `YouTubeSearchClient` | Calls the YouTube Data API v3 and filters out songs that can't play. |
| `BrowserPanel` | The embedded Chromium (JCEF) and the JavaScript ↔ Java bridge. |
| `LocalPlayerServer` | Tiny web server on 127.0.0.1 that serves `player.html` (YouTube rejects local files). |
| `player.html` | The YouTube IFrame Player; tells Java when a song ends or fails. |
| `Track`, `PlaybackListener` | A song, and the events the UI listens to. |
| `MusicPrototype` | Stand-alone test window for the player (not needed by the editor). |
