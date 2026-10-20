# desktop-check

A compile check (and JVM test run) for Recipai's shared code in sandboxes
that can't reach Google's Maven repository or the Android SDK. It is not
part of the app build and CI does not run it; GitHub Actions remains the
real Android compiler.

```sh
./gradlew -p tools/desktop-check :core:test :ui:compileKotlin :ui:test
```

How it works: `:ui` compiles `app/src/main/java` against Compose
Multiplatform Desktop 1.7.0, whose APIs match the app's Compose BOM
(Compose 1.7, Material3 1.3). Android-only code is excluded (see the list
in `ui/build.gradle.kts`): anything in a `platform` package, plus a few
named files. `stubs/` gives each excluded file a same-API stand-in, at
the same package path (`stubs/com/blackchapel/recipai/platform/...`); the
excludes apply to the app's sources only, never to `stubs/`.

Rules for app code, so this check stays useful:

- Anything that touches Android, androidx-only or Google Play APIs
  (Context, Intent, Uri, services, WorkManager, Credential Manager,
  photo picker launchers, Coil, DataStore) goes in a `platform` package,
  with a stub here exposing the same public signatures.
- Stub files are named `<Name>Stub.kt`.
- Shared UI uses only Compose/Material3 APIs that exist in common code.
