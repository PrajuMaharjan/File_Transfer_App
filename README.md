This is a Kotlin Multiplatform project targeting Android, Desktop (JVM).

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: 
  - `./gradlew :androidApp:installDebug`
  - `adb shell monkey -p your.package.name -c android.intent.category.LAUNCHER 1`
- Desktop app:
  - Hot reload: `./gradlew :desktopApp:hotRun --auto`
  - Standard run: `./gradlew :desktopApp:run`
