# NexaFlow

**Advanced Context-Aware Automation Engine for Android**

NexaFlow is a professional automation platform inspired by Samsung Modes & Routines and Tasker, designed to provide a more powerful, customizable, and extensible automation experience.

## Features

- **Context-Aware Automation:** Create intelligent device automations using WHEN → IF → THEN logic.
- **Modular Architecture:** Built with a clean, modular structure for scalability and maintainability.
- **Modern UI:** Utilizes Jetpack Compose and Material 3 for a premium user experience.
- **Extensible:** Designed to allow easy integration of future features like AI recommendations.

## Technology Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Architecture:** Clean Architecture, MVVM, Repository Pattern
- **Dependency Injection:** Hilt
- **Database:** Room
- **Storage:** DataStore
- **Asynchronous Operations:** Kotlin Coroutines + Flow
- **Background Tasks:** WorkManager, AlarmManager, Foreground Services
- **Navigation:** Navigation Compose
- **Minimum SDK:** Android 10 (API 29)

## Project Structure

The project follows a modular structure:

```
.github/
app/
core/
  common/
  database/
  datastore/
  permissions/
  automation-engine/
  execution/
  capability-manager/
  ui-components/
  security/
domain/
  models/
  repositories/
  usecases/
data/
  local/
  repository/
  mapper/
feature/
  dashboard/
  automation-builder/
  automations/
  profiles/
  history/
  icons/
  themes/
  widgets/
  settings/
```

## Getting Started

To build and run NexaFlow, you will need Android Studio Dolphin or newer.

1. Clone the repository:
   ```bash
   git clone https://github.com/Alaa91H/NexaFlow.git
   ```
2. Open the project in Android Studio.
3. Build and run on an Android 10+ device or emulator.

## Contributing

We welcome contributions! Please see `CONTRIBUTING.md` for details.

## License

This project is licensed under the MIT License - see the `LICENSE` file for details.
