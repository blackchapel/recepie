import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin, no Android: recipe model, quantity/unit scaling and the
// rule-based importer. Kept separate so it can be unit-tested on any JVM
// (CI runs :core:test) without the Android SDK.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
}
