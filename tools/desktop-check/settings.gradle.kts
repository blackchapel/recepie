// A compile check for the app's shared Compose code that runs without the
// Android SDK (see README.md in this folder). Not part of the app build.
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "desktop-check"
include(":core")
project(":core").projectDir = file("../../core")
include(":ui")
