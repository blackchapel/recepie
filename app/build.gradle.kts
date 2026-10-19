import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The single source of truth for the app's semantic MAJOR.MINOR -- bump it
// by hand in the PR that earns it (see CLAUDE.md). CI appends the patch or
// branch metadata on top; this is only the fallback for local builds.
val baseVersionName = Properties().apply {
    val propsFile = file("version.properties")
    if (propsFile.exists()) propsFile.inputStream().use { load(it) }
}.getProperty("VERSION_NAME", "0.1")

android {
    namespace = "com.blackchapel.recipai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.blackchapel.recipai"
        minSdk = 26
        targetSdk = 35
        // CI always passes both explicitly (computed from git in the
        // workflows). Local builds fall back to a plain, never-published
        // version.
        versionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("appVersionName") as String?) ?: baseVersionName
    }

    signingConfigs {
        create("release") {
            // Populated by CI from the KEYSTORE_* secrets (see
            // .github/workflows/build-apk.yml and release.yml). Left unset
            // for a local build -- assembleRelease without them fails at
            // signing time, which is correct: there's no meaningful
            // unsigned fallback for a release build.
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (!keystorePath.isNullOrEmpty()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME is shown in Settings > About.
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // Photos: loading and downscaling, EXIF orientation.
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // Google Drive backup: authorization and scheduled backups.
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
