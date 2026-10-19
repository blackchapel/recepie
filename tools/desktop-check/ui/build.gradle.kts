import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
}

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

val app = rootDir.resolve("../../app/src").canonicalFile

// Android-only code: never compiled here. Everything in a `platform`
// package, plus these files. Each has a stub with the same public API in
// ../stubs (files named *Stub.kt so these excludes don't hide them).
val androidOnly = listOf(
    "**/platform/**",
    "**/MainActivity.kt", "**/MainViewModel.kt", "**/RecipaiApplication.kt",
    "**/data/DataStoreSettings.kt", "**/haptics/Haptics.kt",
    "**/ui/theme/Fonts.kt", "**/ui/components/Icons.kt", "**/ui/Platform.kt",
)
sourceSets["main"].kotlin.srcDir(app.resolve("main/java"))
sourceSets["main"].kotlin.srcDir(rootDir.resolve("stubs"))
sourceSets["main"].kotlin.exclude(androidOnly)
// The app's JVM unit tests (app/src/test) run here too.
sourceSets["test"].kotlin.srcDir(app.resolve("test/java"))
sourceSets["test"].kotlin.exclude(androidOnly)

dependencies {
    implementation(project(":core"))
    // Compose Multiplatform 1.7.0 == Jetpack Compose 1.7 / Material3 1.3,
    // the versions in the app's Compose BOM.
    val cmp = "1.7.0"
    implementation("org.jetbrains.compose.runtime:runtime-desktop:$cmp")
    implementation("org.jetbrains.compose.ui:ui-desktop:$cmp")
    implementation("org.jetbrains.compose.foundation:foundation-desktop:$cmp")
    implementation("org.jetbrains.compose.material3:material3-desktop:$cmp")
    implementation("org.jetbrains.compose.animation:animation-desktop:$cmp")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

// androidx.* artifacts live only on Google Maven, which a sandbox without
// Android access cannot reach; Compose Desktop needs them only at runtime.
configurations.all {
    exclude(group = "androidx.annotation")
    exclude(group = "androidx.collection")
    exclude(group = "androidx.lifecycle")
    exclude(group = "androidx.arch.core")
}
