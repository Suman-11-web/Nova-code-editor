# Nova Code Editor

Nova Code Editor is a high-performance, visually polished, and fully-featured code editing and management application built for Android. It is styled with an **Elegant Dark** theme featuring high-contrast syntax highlighting, custom auxiliary coding panels, project explorer navigation, and interactive code execution capabilities.

---

## 🎨 Design & Aesthetic

The application utilizes a premium, custom-crafted **Elegant Dark Theme** designed to offer a comfortable, eye-friendly coding experience over long sessions:

*   **Deep Canvas Palette**: Uses deep dark background tones (`#1C1B1F` and `#25232A`) paired with bright, modern neon accents (`#D0BCFF` primary, `#EFB8C8` strings, `#CCC2DC` operators).
*   **Aesthetic Typography**: Styled with high-contrast Material 3 display headings, paired with custom monospace typography for precise code readability.
*   **Spacious & Minimalist Layout**: Adheres to Material Design 3 density guidelines with generous, consistent padding, clean borders, and clear divider hierarchies.

---

## 🚀 Key Features

1.  **Rich Code Editor**:
    *   Dynamic code tabs with multi-file support.
    *   Interactive auxiliary programming symbol keyboard (`{`, `}`, `(`, `)`, `[`, `]`, `;`, `TAB`).
    *   Configurable font size, line wrapping, and theme toggle.
2.  **Integrated Project Explorer**:
    *   Full directory hierarchy traversal.
    *   Create, view, and manage files and subfolders locally.
3.  **Run Console**:
    *   Interactive runner interface to simulate compiling and logging program stdout/stderr logs.
4.  **Custom Settings panel**:
    *   Configure text size, toggle word wrapping, choose syntax highlighter themes.

---

## 🛠️ Technology Stack

Nova Code Editor is developed utilizing modern Android development practices and libraries:

*   **Jetpack Compose**: 100% Kotlin-based declarative UI framework for building modern, responsive, and adaptive layouts.
*   **Material Design 3 (M3)**: Full integration of Material 3 components (`Scaffold`, `NavigationBar`, `TopAppBar`, `Card`, `FloatingActionButton`, `TextField`, etc.) with customized tokenized color schemes.
*   **Kotlin Coroutines & Flow**: Used for non-blocking asynchronous state loading, file-system indexing, and smooth state updates.
*   **Model-View-ViewModel (MVVM)**: Structured state encapsulation using `ViewModel` and `MutableStateFlow` to preserve UI stability and robust state handling.
*   **Gradle 9.3.1 Wrapper**: Pre-configured Gradle distribution wrapper ensures absolute build predictability, reproducible builds, and seamless import into Android Studio or other build servers.
*   **Kotlin DSL (`.gradle.kts`)**: Pure type-safe build configuration files for modular dependency declaration.
*   **GitHub Actions CI/CD**: Automatic build runner that compiles and packages the application into debug and release APK artifacts on every push or pull request.

---

## 💻 Building and Running

### Prerequisites
*   **JDK 21** or higher.
*   **Android Studio** (Koala or newer recommended).

### Local Development Setup
1.  Clone the repository:
    ```bash
    git clone <your-repository-url>
    cd <repository-directory>
    ```
2.  Open the project in Android Studio.
3.  Run the application using the default run configuration on your preferred physical device or virtual emulator.

### Gradle Command-Line Builds
The project includes the pre-configured Gradle Wrapper scripts (`gradlew` and `gradlew.bat`), meaning you can build the project from the terminal without having Gradle manually installed on your local operating system.

*   **To compile and assemble a Debug APK:**
    ```bash
    ./gradlew assembleDebug
    ```
    *Output location:* `app/build/outputs/apk/debug/app-debug.apk`

*   **To compile and assemble a Release APK:**
    ```bash
    ./gradlew assembleRelease
    ```
    *Output location:* `app/build/outputs/apk/release/app-release-unsigned.apk`

---

## 📦 Project Structure

```text
├── .github/
│   └── workflows/
│       └── android.yml          # GitHub Actions CI automated build script
├── app/
│   ├── build.gradle.kts         # App-level dependency configuration
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/example/
│           │       ├── MainActivity.kt    # Primary activity with core navigation
│           │       ├── data/              # Data structures and repository logic
│           │       └── ui/                # UI Screens & Layouts
│           │           ├── editor/        # Code editor and themes
│           │           ├── explorer/      # File system navigator
│           │           ├── console/       # Simulation logs output
│           │           ├── settings/      # Application settings
│           │           └── theme/         # Color palettes, central Theme.kt
│           └── res/                       # App resources (strings, drawable icons)
├── gradle/
│   ├── wrapper/
│   │   ├── gradle-wrapper.jar             # Executable binary for the Gradle build runner
│   │   └── gradle-wrapper.properties      # Target Gradle distribution configuration
│   └── libs.versions.toml                 # Version Catalog for centralized dependency management
├── settings.gradle.kts          # Root settings declaring project modules
├── build.gradle.kts             # Project-level plugins configurations
├── gradlew                      # Executable Linux/macOS shell script for the wrapper
└── gradlew.bat                  # Executable Windows command script for the wrapper
```

---

## 🤖 Continuous Integration with GitHub Actions

Every commit pushed to the `main`, `master`, or `develop` branches triggers the workflow defined in `.github/workflows/android.yml`.

The runner automatically performs:
1.  **Source Code Checkout**: Pulls down the latest version of the repository.
2.  **JDK 21 Setup**: Configures a secure, isolated Eclipse Temurin JDK 21 build container environment with automated Gradle caching for incredibly fast, incremental builds.
3.  **Permissions Elevation**: Automatically runs `chmod +x gradlew` to guarantee executable permissions.
4.  **Application Compiling**: Automatically calls `./gradlew assembleDebug` and `./gradlew assembleRelease` to run linting and compile the app.
5.  **Artifact Archiving**: Saves the resulting `app-debug.apk` and `app-release-unsigned.apk` directly into the Actions Run dashboard so you can download and install them immediately.
