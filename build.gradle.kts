// Top-level build file for the Global VPN Android project.
// Kotlin 2.1.20: required because current Google libraries (Firebase BoM
// 34.8.0 -> play-services-measurement-api 23.0.0, play-services-ads
// 23.0.0) ship Kotlin classes compiled with metadata 2.1.0, which the
// Kotlin 1.9 compiler cannot read ("binary version of its metadata is
// 2.1.0, expected version is 1.9.0"). KGP 2.1.20 supports Gradle
// 7.6.3-8.11 and AGP 7.3.1-8.7.2, so the project's Gradle 8.7 and AGP
// 8.5.2 stay as-is. The Compose compiler plugin (same version) replaces
// the old kotlinCompilerExtensionVersion setting in app/build.gradle.kts.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.0" apply false
}
