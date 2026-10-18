# ⚡ KiloWatt Application Architecture Specification

## 📄 Overview
**KiloWatt** is an AI-powered smart utility meter reading Android application. It captures live camera frames, processes digital and analog electric meter displays using on-device Optical Character Recognition (OCR), extracts meter readings and anomaly fault codes, calculates real-time electricity tariff quotes, and computes rooftop solar energy savings potential.

---

## 🏗️ Architectural Pattern
The application follows modern Android Development (MAD) best practices utilizing the **MVVM (Model-View-ViewModel)** architectural pattern with **Unidirectional Data Flow (UDF)**.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           PRESENTATION LAYER                                │
│                                                                             │
│  [MainActivity]  ──►  [KiloWattApp]  ──►  [CameraView]                      │
│                                           [ScanningReticleOverlay]          │
│                                           [ScanResultBottomSheetCard]       │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ Observes StateFlow<MeterUiState>
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                             DOMAIN & STATE LAYER                            │
│                                                                             │
│  [MeterViewModel]                                                           │
│  ├── State: StateFlow<MeterUiState>                                         │
│  ├── Tariff Engine ($0.18 / kWh)                                            │
│  └── Solar Potential Calculator (Panel Count & Annual Savings)              │
└──────────────────────────────────────▲──────────────────────────────────────┘
                                       │ Invokes Callbacks
┌─────────────────────────────────────────────────────────────────────────────┐
│                         VISION & ANALYSIS LAYER                             │
│                                                                             │
│  [CameraX ImageAnalysis]  ──►  [MeterImageAnalyzer]                         │
│                                ├── Google ML Kit Text Recognition (Latin)   │
│                                ├── Regex Engine (\b\d{4,6}(\.\d{1,2})?\b)    │
│                                └── Anomaly Detector (ERR, WARN, FAULT)      │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 🗂️ Component Directory & Responsibilities

### 1. Presentation Layer (`MainActivity.kt`)
- **[MainActivity.kt](file:///Users/manojmouryaneeli/Documents/Android%20Projects/kilowatt/app/src/main/java/com/inouiw/kilowatt/MainActivity.kt)**
  - **`KiloWattApp`**: Root Composable handling runtime camera permission initialization (`ActivityResultContracts.RequestPermission`).
  - **`CameraView`**: Encapsulates CameraX `PreviewView` using `AndroidView`. Binds camera preview and `ImageAnalysis` pipelines to the activity `LifecycleOwner`.
  - **`ScanningReticleOverlay`**: Composes the camera HUD reticle using `Canvas` with an animated vertical laser beam (`#00FF88`) and electric corner brackets (`#00E5FF`).
  - **`TopHeaderBar`**: Displays application identity and live scanning state indicator badges.
  - **`ScanResultBottomSheetCard`**: Animated Material 3 bottom card presenting verified meter readings, dynamic tariff quotes, solar panel recommendations, and fault alert banners.
  - **`CameraPermissionRequestScreen`**: Onboarding fallback screen displayed when camera access is denied.

### 2. State Management Layer (`MeterViewModel.kt`)
- **[MeterViewModel.kt](file:///Users/manojmouryaneeli/Documents/Android%20Projects/kilowatt/app/src/main/java/com/inouiw/kilowatt/MeterViewModel.kt)**
  - **`MeterUiState`**: Immutable state data class containing:
    - `hasCameraPermission`: Permission grant status.
    - `isScanning`: Flag determining active laser scanner state.
    - `detectedKWh`: Verified double value of electric meter reading.
    - `instantTariffRate`: Base grid rate ($0.18 / kWh).
    - `estimatedMonthlyBill`: Monthly bill calculation based on reading.
    - `solarPanelCount`: Recommended rooftop solar panel count (`monthlyKwh / 35.0`).
    - `solarAnnualSavings`: Projected yearly clean energy savings ($210/panel).
    - `hasFault` & `faultMessage`: Meter error code alerts.
    - `isReadingVerified`: Flag triggering UI bottom card slide-in.
  - **`onMeterReadingDetected()`**: Business logic processor that updates state upon new valid readings from the analysis layer.
  - **`resetScan()`**: Resets scan state to allow subsequent meter readings.

### 3. Vision Analysis Layer (`MeterImageAnalyzer.kt`)
- **[MeterImageAnalyzer.kt](file:///Users/manojmouryaneeli/Documents/Android%20Projects/kilowatt/app/src/main/java/com/inouiw/kilowatt/MeterImageAnalyzer.kt)**
  - Implements CameraX `ImageAnalysis.Analyzer`.
  - Processes frames asynchronously via Google ML Kit `TextRecognition.getClient(...)`.
  - Parses meter kWh numbers using regex `\b\d{4,6}(\.\d{1,2})?\b`.
  - Detects anomaly keywords (`ERR`, `WARN`, `FAULT`).
  - Safely closes `ImageProxy` frames via `addOnCompleteListener` to prevent memory leaks and frame buffer deadlocks.

---

## 🛠️ Technology Stack & Dependencies

| Category | Technology / Dependency | Version | Purpose |
| :--- | :--- | :--- | :--- |
| **Language** | Kotlin | `2.0.21` | Modern idiomatic Kotlin development |
| **UI Framework** | Jetpack Compose + Material 3 | BOM `2024.09.00` | Declarative UI and adaptive components |
| **Camera Hardware** | CameraX | `1.4.0` | Camera lifecycle management and frame analysis |
| **ML / OCR** | Google ML Kit Text Recognition | `16.0.1` | On-device text recognition |
| **Architecture** | ViewModel Compose | `2.8.6` | Lifecycle-aware UI state holder |
| **Target SDK** | Android SDK | `37` (Compile) / `35` (Target) | Android platform APIs |

---

## 🔐 Security & Permissions

1. **Permissions:**
   - `android.permission.CAMERA`: Required for camera feed scanning.
   - `android.permission.INTERNET`: Declared for network access.
2. **Hardware Features:**
   - `<uses-feature android:name="android.hardware.camera" android:required="true" />`
3. **On-Device Privacy:**
   - Optical character recognition runs **100% on-device** using ML Kit local binaries. Camera frames are analyzed in memory and immediately disposed of. No camera video streams or images leave the local runtime environment.
