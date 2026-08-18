# Weather News App

> A weather/news Android app built with MVVM + Jetpack (Kotlin, Hilt, Retrofit, Room, DataStore, Espresso). Stage 1 learning project (Day 1-19).

## Features

- Real-time weather via Open-Meteo API (city switch, C/F toggle)
- Chinese news via TianAPI (pull-to-refresh, fallback to fake data)
- Offline weather via Room cache
- User settings via DataStore (city / temperature unit / news channel)
- 3-tier testing pyramid (ViewModel unit + Repository unit + Espresso UI + Jacoco)

## Architecture

```
UI (Fragment)  ->  ViewModel (StateFlow)  ->  Repository  ->  [Network / Room / DataStore]
```

See `app/src/main/java/com/example/weathernewsapp/` for full layout.

## Tech Stack

| Layer | Tech |
|-------|------|
| Async | Kotlin Coroutines + Flow |
| Network | Retrofit + OkHttp + Gson |
| Local cache | Room |
| Preferences | DataStore |
| DI | Hilt |
| Navigation | Jetpack Navigation |
| Image | Coil |
| Test | JUnit 4 + MockK + Espresso + Jacoco |
| Lint | ktlint 12.1.0 |

## Run

1. Clone this repo
2. Add your TianAPI key to `local.properties`: `TIANAPI_KEY=your_key_here`
3. Open in Android Studio Hedgehog or later (JDK 11, SDK 35)
4. Run on emulator API 28+ or real device

## Test

```powershell
# JVM unit tests
.\gradlew.bat testDebugUnitTest

# UI tests on connected device
.\gradlew.bat :app:connectedDebugAndroidTest

# Coverage report
.\gradlew.bat :app:createDebugUnitTestCoverageReport

# Static analysis
.\gradlew.bat :app:ktlintCheck
```

## Branching

- `main` - stable, tag v1.0-stage1 after Day 19
- `develop` - integration branch
- `feature/dayXX-*` - per-day learning feature branches (merged into develop via --no-ff)

## License

MIT
