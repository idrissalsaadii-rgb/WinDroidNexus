// Top-level build file. Plugin versions are declared here with `apply false`
// so that sub-modules can apply them without re-declaring the version.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    // Kotlin 2.0+ ships the Compose compiler as a standalone Gradle plugin.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("com.google.devtools.ksp") version "2.0.20-1.0.25" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
}
