# WinDroid Nexus

An Android application that presents an **original, Windows‑style desktop
environment** (centered taskbar, Start menu, draggable/resizable windows,
touch‑to‑mouse input) on top of a **pluggable native execution engine** capable
of driving PRoot / Box64+Wine / QEMU backends to run `.exe` and Linux binaries.

Built with **Kotlin · Jetpack Compose · Clean Architecture (MVVM + Repository)
· Hilt · Coroutines/Flow**.

> **Design & IP note.** The shell is an *original* interpretation of a desktop
> UI. It bundles **no** Microsoft artwork — the wallpaper is generated in code,
> icons are generic Material vectors, and the Start glyph is a neutral “apps”
> mark. If you want exact Windows 11 branding you must supply your own licensed
> assets (drop‑in points are noted in `Wallpaper.kt` and `WinIcons.kt`).

---

## Module / package layout

```
app/src/main/java/com/windroid/nexus/
├── WinDroidApp.kt                 # @HiltAndroidApp application
├── MainActivity.kt                # immersive full‑screen host, system‑UI hiding
├── core/
│   ├── di/AppModule.kt            # Hilt bindings (interface → impl)
│   └── util/                      # Resource, DispatcherProvider
├── domain/                        # pure Kotlin — no Android deps
│   ├── model/Models.kt            # WindowInstance, SystemStats, EngineEvent, …
│   ├── engine/ExecutionEngine.kt  # engine + NativeRuntime contracts
│   ├── repository/                # SystemRepository, VfsRepository, AppRepository
│   └── usecase/                   # LaunchBinary, ObserveSystemStats, …
├── data/
│   ├── engine/
│   │   ├── EngineBridge.kt        # ← the process manager / abstraction layer
│   │   ├── EngineForegroundService.kt
│   │   └── runtime/               # PRoot / Box64+Wine / QEMU strategies + factory
│   ├── vfs/VfsManager.kt          # C:\ ⇆ Android storage, path‑traversal safe
│   ├── system/SystemMonitor.kt    # CPU (/proc/stat) · RAM · battery · network
│   └── repository/AppRepositoryImpl.kt
└── presentation/
    ├── theme/                     # original Fluent‑inspired palette + type
    ├── desktop/                   # DesktopScreen + DesktopViewModel + Wallpaper
    ├── taskbar/Taskbar.kt         # centered icons, system tray, live clock
    ├── startmenu/StartMenu.kt     # search + pinned grid
    ├── window/WindowFrame.kt      # drag / resize / min / max / close chrome
    ├── input/CursorOverlay.kt     # touch‑to‑mouse (relative‑movement) cursor
    ├── icons/WinIcons.kt
    └── apps/                      # FileExplorer, Terminal, Browser, Settings,
                                   # TaskManager + AppRegistry
```

### Architecture at a glance

```
presentation ──depends on──▶ domain ◀──implements── data
   (Compose, ViewModels)     (contracts)      (EngineBridge, VfsManager, …)
```

The UI layer only ever touches **domain interfaces**; Hilt binds the concrete
`data` implementations at the composition root. This keeps backends swappable
and the whole domain unit‑testable without Android.

---

## The execution engine (honest summary)

`EngineBridge` is a **real process manager**: it scaffolds the rootfs, discovers
which backend is provisioned, builds the correct argv via a `NativeRuntime`
strategy, launches it with `ProcessBuilder`, and streams stdout/stderr and the
process lifecycle as a cold `Flow<EngineEvent>` (consumed live by the Terminal
and Task Manager).

What it **does not** ship are the large GPL/LGPL native binaries themselves
(PRoot, Box64, Wine, QEMU). That is by design and is exactly how apps in this
class (e.g. Termux/Winlator‑style projects) operate: the user provisions a
runtime into the engine rootfs, and the bridge detects it. When no runtime is
present, launches fail **gracefully** with a clear `EngineEvent.Failed` instead
of crashing. Provisioning hooks live in `Settings → Engine` and in
`data/engine/runtime/`.

Rootfs location: `filesDir/rootfs` (app‑private). Drive mapping:
`C:\ → filesDir/rootfs/drive_c`. Additional drives can be mounted onto
user‑selected folders via `VfsRepository.mountExternal`.

---

## Getting the APK

### Option A — no local setup: let GitHub build it
This repo ships a CI workflow (`.github/workflows/android.yml`) that compiles an
installable `app-debug.apk` on GitHub's runners.

1. Push this project to a GitHub repo.
2. Open the **Actions** tab → the **Build APK** workflow runs automatically
   (or click *Run workflow*).
3. When it finishes, download the **WinDroidNexus-debug-apk** artifact — that
   zip contains `app-debug.apk`.
4. Copy it to your Android device and install (enable *Install unknown apps*).

### Option B — build locally
Requirements: **Android Studio Koala+** or CLI with **JDK 17** + **Android SDK 35**.
The Gradle wrapper is already included, so:

```bash
./gradlew :app:assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug       # build + install to a connected device
```

Create a `local.properties` with your SDK path (Android Studio writes this for
you automatically):

```
sdk.dir=/path/to/Android/sdk
```

> The debug APK is self-signed and installable directly. For a Play Store /
> release build you'd add a signing config and run `assembleRelease`.

### Optional: use as the device home launcher
`MainActivity` declares the `HOME` intent category, so you can set WinDroid
Nexus as your launcher to get a full‑device desktop experience.

---

## Key technical decisions

| Area | Choice | Why |
|------|--------|-----|
| UI | Jetpack Compose + Material 3 | declarative windowing, custom chrome |
| DI | Hilt (KSP) | singleton engine/repos, testable ViewModels |
| Async | Coroutines + `Flow`/`StateFlow` | live console + 1 Hz stats stream |
| Window mgmt | single `DesktopUiState` + z‑index | predictable stacking/focus |
| Engine I/O | `ProcessBuilder` + `callbackFlow` | backpressure‑safe streaming, clean teardown |
| VFS | centralized path translation | one place to enforce no‑traversal |
| Immersive | `WindowInsetsControllerCompat` sticky | true full‑screen desktop |

---

## Testing seams
- `RuntimeFactory` can be replaced with a fake returning stub `NativeRuntime`s.
- `DispatcherProvider` lets tests inject `StandardTestDispatcher`.
- `DesktopViewModel` window logic is pure state transition → straightforward to
  unit‑test (open/focus/minimize/maximize/move/resize).

## License / assets reminder
Ship only assets you have the right to distribute. The bundled shell is
original; Windows®, Windows 11, and related marks belong to Microsoft and are
**not** included.
