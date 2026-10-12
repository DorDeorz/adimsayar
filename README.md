<p align="center">
  <img src="docs/icon.png" width="112" alt="FitWren icon">
</p>

<h1 align="center">FitWren – Step Counter &amp; Health</h1>

<p align="center">
  A private step counter and health tracker for Android.<br>
  No account, no ads, no tracking. Your data never leaves your phone.
</p>

<p align="center">
  <a href="../../releases/latest"><img src="https://img.shields.io/github/v/release/DorDeorz/adimsayar?label=download&amp;color=2E7D32" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-6.0%2B-2E7D32" alt="Android 6.0+">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/DorDeorz/adimsayar?color=2E7D32" alt="MIT license"></a>
</p>

<p align="center">
  <b>English</b> · <a href="README.tr.md">Türkçe</a>
</p>

---

<p align="center">
  <img src="docs/screenshots/today.png" width="200" alt="Today">
  <img src="docs/screenshots/history.png" width="200" alt="History">
  <img src="docs/screenshots/health.png" width="200" alt="Health">
  <img src="docs/screenshots/medals.png" width="200" alt="Medals">
</p>

## Why FitWren

Many phones ship without a step counter app, or with one that wants an account and sends your data to a server. FitWren counts steps with your phone's own sensor, stores everything on the device and works without any sign-in.

- **Accurate.** Reads the hardware step counter and checks it against the step detector, so a frozen sensor is noticed and fixed without a reboot.
- **Battery friendly.** No always-on service by default. The counter is read now and then and the difference is saved.
- **Private.** No account, no server, no analytics, no ads. The internet is used only when you tap "Check for updates".
- **Works on every Android phone.** Tested on Xiaomi (HyperOS) and Samsung (One UI), the two brands that are hardest on background apps.

## Features

### Today
- Progress ring toward your daily goal, steps left, distance and calories
- Streak, weekly progress and the last 7 days
- Water tracking with a goal suggested from your weight
- A little wren that grows with your total steps and reacts to how your day is going. Tap it and it hops.
- Share your day as an image

### History
- Monthly calendar colored by goal, hour-by-hour chart for any day
- Records: best day, week and month, longest streak
- Yearly summary
- Streak freeze: two per month, so one missed day doesn't break your streak

### Health (optional, with a fitness band or watch)
- Reads steps from bands such as the Mi Band through Health Connect. Hours the band counted use the band, other hours use the phone, so no step is counted twice.
- Sleep with stages, heart rate with zones, resting heart rate trend, SpO₂, workouts and active calories
- Weight log with BMI and a chart
- A setup guide that shows which apps write to Health Connect

### Medals and goals
- Medals for daily steps, streaks, weekly goals, total distance and early walks. Once earned, a medal stays.
- Levels, three weekly challenges picked from your own history
- Virtual routes: walk from Istanbul to Ankara or along the Camino with your steps

### 13 home-screen widgets

Today's steps, 7-day chart, streak and week, monthly calendar, today by hour, heatmap, sleep and heart, the wren, water (add a glass with one tap), virtual route, weekly challenges, weight and daily summary.

Every widget resizes to fit, follows your wallpaper colors (Material You) or the app theme, and has adjustable background transparency. Tapping a widget opens the matching page.

<p align="center">
  <img src="docs/screenshots/widgets.png" width="320" alt="Widgets on the home screen">
</p>

### And also
- Daily goal, weekly goal, first day of week, km or miles
- Optional notifications: goal reached, goal almost reached, evening streak reminder, weekly summary, water and weight reminders
- Light, dark or system theme
- 12 languages: English, Turkish, German, Spanish, French, Italian, Portuguese, Russian, Arabic, Japanese, Chinese and Korean
- Export and import as CSV. Import also reads Samsung Health, Mi Fitness, Mi Fit and Google Fit exports.

## Install

1. Download the latest APK from [Releases](../../releases/latest).
2. Open it on your phone and allow installing from this source.
3. On first launch, allow "Physical activity".
4. On Xiaomi phones, turn on Settings → Step source → "Use the HyperOS step record" for counting without a notification.

Updates: Settings → About → "Check for updates" downloads the new APK from GitHub and opens Android's installer. You can also track releases with [Obtainium](https://github.com/ImranR98/Obtainium).

## Privacy

FitWren stores daily step totals, water, weight and your settings on the phone. Nothing is sent anywhere.

The only network request is made when you tap "Check for updates": the app reads the latest version number from `api.github.com/repos/DorDeorz/adimsayar/releases/latest`, and if you tap again it downloads the APK from GitHub Releases. Nothing about you or your steps is sent with it.

Health Connect is used only if you turn on "Use band steps", and only for reading. FitWren never writes to Health Connect.

<details>
<summary>Permissions and why they are needed</summary>

| Permission | Why |
|---|---|
| `ACTIVITY_RECOGNITION` | Read the step sensors (Android 10+) |
| `miui.permission.READ_STEPS` | Read the HyperOS step record on Xiaomi |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH` | Read the counter for a few seconds in the background, and the optional "Keep counting in the background" mode |
| `POST_NOTIFICATIONS` | Optional reminders and goal notifications |
| `RECEIVE_BOOT_COMPLETED` | Restore schedules after a reboot |
| `SCHEDULE_EXACT_ALARM` | Switch to the new day at midnight |
| `INTERNET` | Only the update check and download you start yourself |
| `REQUEST_INSTALL_PACKAGES` | Open Android's installer for a downloaded update |
| `health.READ_*` | Optional band data from Health Connect, read only |

</details>

## How steps are counted

<details>
<summary>Technical details</summary>

The only real data is the **step total per day**. Medal progress, levels, streaks and records are calculated from it.

**Phone sensor (default).** Android's `TYPE_STEP_COUNTER` reports the steps since the phone was turned on. FitWren reads it when the app opens, when a widget updates, every ~15 minutes with `JobScheduler`, and after a reboot or time change. The difference since the last reading is split across days and hours and added to the totals. Android 9+ gives sensors only to active apps, so the background reading runs inside a short foreground service that finishes in under 10 seconds and usually shows no notification.

**Frozen sensor check.** On some phones (Samsung especially) the counter can stop. While the app is open, the step detector is also read. If the detector saw at least 50 steps in two minutes and the counter didn't move, FitWren warns you and re-registers the sensor.

**HyperOS step record (Xiaomi, optional).** HyperOS records steps with its own service even when no app is running. With the switch on, daily and hourly totals come from this record, including walk/run split and past days.

**Keep counting in the background (optional).** For phones that stop counting when the app is killed, a quiet foreground service keeps the counter registered and batches events every 60 seconds.

**Fitness band (optional).** Each Health Connect source is asked for its own daily and hourly totals. The largest source per hour is used, never a sum, and band hours replace phone hours. Band totals live in their own table, so turning the switch off brings the phone's numbers back.

**Battery help.** Some brands kill background apps aggressively. FitWren never forces the battery exemption. If your brand is known for it and the exemption is not granted, a dismissible card shows the steps for your phone.

Design decisions and measurements: [GOALS.md](GOALS.md).

</details>

## Build from source

You need the Android SDK and JDK 21.

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Built with Kotlin, Jetpack Compose, Material 3, Glance and Room. `minSdk 23`, `targetSdk 36`.

```
app/src/main/java/com/dordeorz/adimsayar/
├── sensor/       step sensors, live counter, frozen sensor check
├── data/         Room database, step math, HyperOS and Health Connect sources, medals, routes
├── background/   periodic reading, midnight alarm, services, reminders
├── widget/       13 Glance widgets
└── ui/           Compose screens, theme, battery help
```

Release APKs are built and signed by GitHub Actions with a key kept in repository secrets. Without those secrets (in a fork, for example) the APK is signed with a temporary debug key. Version history: [CHANGELOG.md](CHANGELOG.md) (Turkish; the in-app release notes are translated). How to publish a release: [docs/RELEASING.md](docs/RELEASING.md).

## License

[MIT](LICENSE). FitWren was formerly called AdımSayar; the package name `com.dordeorz.adimsayar` stays the same, so updates and your data are not affected.

Built with [Claude Code](https://claude.com/claude-code). Sister project: [Kronometre](https://github.com/DorDeorz/kronometre).
