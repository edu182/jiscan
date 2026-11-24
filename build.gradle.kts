plugins {
    // Version matrix (fully within official compatibility ranges):
    //   Kotlin 2.0.21 -> AGP 7.1.3-8.5, Gradle 6.8.3-8.8
    //   Compose compiler plugin version matches Kotlin version.
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
