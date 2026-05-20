# Smart Note

Smart Note is an Android note-taking app with authentication, note management, and an AI assistant for note-related help.

## Features

- Email/password and Google sign-in
- Create, edit, view, and delete notes
- Note details and profile screens
- AI Assistant chat for summaries, ideas, translation, and writing help
- Firebase integration for auth and data sync
- ML Kit text recognition support

## Tech Stack

- Kotlin
- Android SDK 35
- Material 3
- Firebase Auth, Analytics, and Realtime Database
- Retrofit and OkHttp
- Coroutines
- ML Kit Text Recognition

## Prerequisites

- Android Studio
- JDK 17
- A configured Firebase project

## Setup

1. Open the project in Android Studio.
2. Make sure `google-services.json` is present in the project as configured.
3. Sync Gradle.
4. If your environment uses a custom backend for the AI assistant, configure the required backend settings in the app code as already wired in the repository.

## Build

```bash
./gradlew assembleDebug
```

On Windows PowerShell, if `JAVA_HOME` is not already set correctly:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
./gradlew.bat assembleDebug
```

## Run

Open the project in Android Studio and run the `app` module on an emulator or device.

## Project Structure

- `app/src/main/java/.../ui` - Activities and screens
- `app/src/main/java/.../adapters` - RecyclerView adapters
- `app/src/main/java/.../models` - Data models
- `app/src/main/java/.../repository` - Firebase and backend access
- `app/src/main/res/layout` - UI layouts
- `app/src/main/res/drawable` - App icons and assets

## Notes

- The project currently targets Android 14 and compiles with SDK 35.
- The AI assistant chat uses separate AI and user message layouts.
- If you change backend or Firebase settings, rebuild the app after updating the config files.
