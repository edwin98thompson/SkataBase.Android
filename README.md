# SkataBase

An Android app for collecting and reviewing skateboard motion data from an ESP32 IMU over Bluetooth Low Energy (BLE). It displays live orientation and sensor battery, records threshold-triggered motion events in a background training session, and supports replaying and annotating recorded tricks.

## Features

- Automatically scans for and connects to the first BLE device whose advertised name contains `skate`, ignoring letter case.
- Displays live roll, pitch, and yaw, plus a 3D skateboard orientation view.
- Shows the connected sensor's battery percentage in the app-wide status bar.
- Provides calibration controls for Madgwick beta, gyroscope, accelerometer, and magnetometer.
- Records IMU event data in a foreground service, so a training session can continue while navigating the app or with the screen locked.
- Detects events using configurable gyro magnitude start and end thresholds, with configurable pre-event and post-event sample buffers.
- Saves training session folders and event CSVs under `Downloads/recordings`.
- Replays event CSVs in a loop and lets you annotate trick type and outcome.
- Copies classified event CSVs into `Downloads/recordings/classified/<label>/`; annotation metadata is stored in the app's private SQLite database.

## Requirements

- Android Studio with an Android SDK that supports API 35.
- Android device running Android 12 (API 31) or later.
- BLE peripheral running compatible firmware and advertising a device name containing `skate`.
- The BLE firmware must send the packet layout expected by `IMUPacketDecoder` and support the command IDs used by the app.

## Build and run

1. Open the repository in Android Studio.
2. Allow Gradle sync to complete.
3. Connect an Android device or start an emulator.
4. Run the `app` configuration.

Or build from the repository root:

```powershell
.\gradlew.bat :app:assembleDebug
```

The app requests Bluetooth scan and connect permissions at launch. Grant them to allow automatic scanning and connection. Training also requires notification permission on Android 13 and later so its foreground service can show its ongoing notification.

> BLE operation requires compatible hardware. An emulator generally cannot replace a physical BLE peripheral for end-to-end testing.

## Using the app

### Connect and view sensor data

Launch the app and grant Bluetooth permissions. It scans for a matching device and connects to the first recognized match. The Scanner tab can also be used to scan and connect manually. The Orientation and Animation tabs show sensor orientation data.

### Calibrate the sensor

Open Calibration to change the Madgwick beta value or start gyro, accelerometer, and magnetometer calibration. The gyro and accelerometer apply switches send enable or disable commands to the connected device.

### Record a training session

Open Training, set the gyro magnitude start/end thresholds and pre/post-event sample counts, then tap **Start Training**. The start threshold must exceed the end threshold. The service writes session information and detected event files beneath `Downloads/recordings`. Use Pause, Resume, and Stop to control the session.

### Review and annotate events

From Training, choose **Review recorded sessions**, select a training session folder, and review its event files. Each event loops in the 3D view until you pause it or move to another event. Choose a trick label and outcome, then tap **Save & next**. The source event remains in its session folder; a copy is written into its classification folder.

The built-in trick labels are Ollie, Kickflip, Heelflip, and Tre Flip. Edit `TrickLabelCatalog` in `app/src/main/java/com/example/myfirstapp/training/TrickLabels.kt` to add or rename labels. Label keys are persisted in annotation records and used as destination folder names, so keep existing keys stable after recording annotations.

## Data formats and storage

Training sessions are stored in shared Downloads so they can be accessed from the phone's file manager:

```text
Downloads/
└── recordings/
    ├── training_<timestamp>/
    │   ├── session_info.csv
    │   ├── event_0001.csv
    │   └── event_0002.csv
    └── classified/
        ├── ollie/
        ├── kickflip/
        ├── heelflip/
        └── tre_flip/
```

Each event CSV contains timestamped IMU samples, including acceleration, gyroscope, magnetometer, orientation, and quaternion values. Annotation metadata (source, classification, outcome, duration, and classified copy location) is kept in the app's private database. Removing app data removes this metadata; the CSV files in Downloads are separate.

## Project structure

- `app/src/main/java/com/example/myfirstapp/ble`: BLE scanning, connection, packet notifications, and commands.
- `app/src/main/java/com/example/myfirstapp/model`: IMU packet, decoder, and app data types.
- `app/src/main/java/com/example/myfirstapp/repository`: repository exposing BLE data streams.
- `app/src/main/java/com/example/myfirstapp/screens`: Compose screens and app navigation.
- `app/src/main/java/com/example/myfirstapp/training`: training foreground service, event recording, labels, and annotation storage.
- `app/src/main/java/com/example/myfirstapp/viewmodel`: screen state and BLE command handling.
- `app/src/main/assets/models/skateboard.glb`: 3D model used by animation and event review.

## Technical overview

The app is written in Kotlin with Jetpack Compose and Material 3. BLE ownership is shared through `AppGraph`, allowing the activity and training foreground service to use the same BLE manager and repository. UI state is exposed through Kotlin Flows and StateFlows. The animation and event review scenes use SceneView.

Android configuration is in `app/build.gradle.kts`; the project uses `minSdk 31`, `compileSdk 35`, and `targetSdk 35`.
