# Architecture

KiloWatt India is an Android app (Kotlin, Jetpack Compose) with a small FastAPI ingestion service. The app follows **MVVM** with a **repository** layer. Dependencies point inward: screens depend on ViewModels, ViewModels depend on repositories, and repositories depend on the domain rules and on the data sources. The domain layer depends on nothing Android-specific.

## Layers

| Layer | Package | Responsibility | Depends on |
|---|---|---|---|
| Domain | `domain` | Models, estimates, tariff, geometry, reading merge. Pure Kotlin, unit tested. | Nothing |
| Data | `data.local`, `data.remote`, `data.repository` | Phone storage, HTTP client, repositories | Domain |
| Sync | `sync` | WorkManager upload job and scheduler | Data |
| ML / AR | `ml`, `ar` | TensorFlow Lite meter reader, ARCore renderer | Domain (for geometry) |
| UI | `ui.*` | Compose screens and ViewModels | Data, Domain, ML, AR |
| DI | `di`, `KiloWattApplication` | Creates the repositories once | Data |

## Screens and ViewModels

| Screen | ViewModel | Reads from | Actions |
|---|---|---|---|
| Launch | (none) | Nothing | Waits 2.4 s, then Home |
| Home | `HomeViewModel` | `ReadingRepository` (merged readings) | Scan, balcony, open History |
| Scanner | `ScannerViewModel` | `TfLiteMeterReader` suggestions | Shutter, confirm reading |
| Reading sheet | `ScannerViewModel` | Suggestion and confirmed state | Yes, Edit / Save, scan again |
| Balcony AR | `BalconyViewModel` | ARCore renderer state | Save survey |
| History | `HistoryViewModel` | Readings, surveys, server status | Filter, refresh on open |
| Telemetry | Uses Home state | Latest reading | Back to Home |

Screens are stateless: they draw the state they are given and send actions back. Navigation is in `AppNavigationViewModel`, and `KiloWattApp` wires the ViewModels to the screens.

## Reading lifecycle

```
Scanner suggests value ──► user confirms ──► PendingReadingStore (phone, JSON)
                                                   │
                       WorkManager (network connected, on launch, every 15 min)
                                                   ▼
                              POST /api/v1/readings ──► 201 Created?
                                                   │         │
                                                  yes        no → retry with backoff
                                                   ▼
                              remove from phone; server copy is the record
```

Rules:
- A reading is written to the phone before any upload is attempted.
- A reading is removed from the phone only after `201 Created`.
- History shows a reading once: the server copy if it exists, otherwise the phone copy.
- If the server cannot be reached, History shows only the phone copies and says so.

## Balcony lifecycle

1. `ArSessionFactory` checks that ARCore is supported and creates a session with horizontal plane detection.
2. `ArBalconyRenderer` draws the camera image and, on every frame, picks the largest tracked floor plane.
3. Span and area are kept over the last 60 frames (about two seconds). The survey is only marked stable when both stay within 10%.
4. "Lock Dimensions & Save" stores the survey on the phone. Surveys are not uploaded yet.

## Domain rules

| Rule | Where | Test |
|---|---|---|
| Energy charge by slab (first 100 units only) | `TariffSchedule` | `TariffScheduleTest` |
| Panels that fit: 60% of area ÷ 2.6 m² | `Estimates.panelsFor` | `EstimatesTest` |
| Yearly generation: panels × 0.55 kW × 1300 kWh/kWp | `Estimates.yearlyGenerationKwh` | `EstimatesTest` |
| Monthly usage from two readings at least a day apart | `Estimates.monthlyUsageKwh` | `EstimatesTest` |
| Merge phone and server readings without duplicates | `mergeReadings` | `ReadingMergeTest` |
| Polygon area and steady measurement | `Geometry` | `GeometryTest` |

## Server

`backend/main.py` is a FastAPI service backed by SQLite.

- `POST /api/v1/readings` inserts or replaces a reading by id and returns `201 Created`.
- `GET /api/v1/readings` returns readings, newest captured first.
- The server stores kWh only. Tariff and bill calculations stay in the app.

## Configuration

- `INGESTION_BASE_URL` is set in `app/build.gradle.kts` (`buildConfigField`).
- The ARCore dependency is `com.google.ar:core`. The manifest marks ARCore as optional, so the app still runs without it.

## Known gaps

- No authentication on the ingestion API.
- The TensorFlow digit model has not been measured for accuracy on real meters.
- Native libraries from TensorFlow Lite and ARCore are not all 16 KB page-aligned, which shows a warning on Android 15+.
- Balcony surveys are not uploaded.
- Telemetry shows only the latest reading and its status.
