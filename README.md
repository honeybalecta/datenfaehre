# android · Lauf 3 · success · 2026-11-10T06:46:54Z
Commit: 1bfba612f12755d9ab58c2184589b8bd7b4e89bc

## build.log
```
V2 Signer: certificate DN: CN=Honey Balecta, OU=Datenfaehre, O=honeybalecta, C=DE
V2 Signer: certificate SHA-256 digest: d5d7fb47399a7fc554b587e2426c00dd5a90966d371098a90a4adff7188b5756
----- letzte Zeilen -----
> Task :app:desugarReleaseFileDependencies
> Task :app:mergeReleaseStartupProfile

> Task :app:stripReleaseDebugSymbols
Unable to strip the following libraries, packaging them as they are: libandroidx.graphics.path.so. Run with --info option to learn more.

> Task :app:processReleaseResources
> Task :app:extractReleaseNativeSymbolTables
> Task :app:mergeReleaseArtProfile
> Task :app:mergeReleaseNativeDebugMetadata NO-SOURCE
> Task :app:mergeReleaseShaders
> Task :app:compileReleaseShaders NO-SOURCE
> Task :app:generateReleaseAssets UP-TO-DATE
> Task :app:mergeReleaseAssets
> Task :app:compressReleaseAssets
> Task :app:extractReleaseVersionControlInfo
> Task :app:collectReleaseDependencies
> Task :app:optimizeReleaseResources
> Task :app:sdkReleaseDependencyData
> Task :app:validateSigningRelease
> Task :app:writeReleaseAppMetadata
> Task :app:writeReleaseSigningConfigVersions
> Task :app:mergeExtDexRelease

> Task :app:compileReleaseKotlin
w: file:///home/runner/work/datenfaehre/datenfaehre/android/app/src/main/java/de/honeybalecta/datenfaehre/MainActivity.kt:66:18 This declaration overrides a deprecated member but is not marked as deprecated itself. Add the '@Deprecated' annotation or suppress the diagnostic.
w: file:///home/runner/work/datenfaehre/datenfaehre/android/app/src/main/java/de/honeybalecta/datenfaehre/MainActivity.kt:67:15 'fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Unit' is deprecated. This method has been deprecated in favor of using the Activity Result API
      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt
      contracts for common intents available in
      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for
      testing, and allow receiving results in separate, testable classes independent from your
      activity. Use
      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)} passing
      in a {@link RequestMultiplePermissions} object for the {@link ActivityResultContract} and
      handling the result in the {@link ActivityResultCallback#onActivityResult(Object) callback}.
w: file:///home/runner/work/datenfaehre/datenfaehre/android/app/src/main/java/de/honeybalecta/datenfaehre/ui/Oberflaeche.kt:288:18 'val LocalClipboardManager: ProvidableCompositionLocal<ClipboardManager>' is deprecated. Use LocalClipboard instead which supports suspend functions.

> Task :app:compileReleaseJavaWithJavac
> Task :app:bundleReleaseClassesToCompileJar
> Task :app:bundleReleaseClassesToRuntimeJar
> Task :app:processReleaseJavaRes
> Task :app:compileReleaseUnitTestKotlin
> Task :app:dexBuilderRelease
> Task :app:compileReleaseUnitTestJavaWithJavac NO-SOURCE
> Task :app:processReleaseUnitTestJavaRes
> Task :app:testReleaseUnitTest
> Task :app:mergeReleaseGlobalSynthetics
> Task :app:mergeReleaseJavaResource
> Task :app:mergeDexRelease
> Task :app:compileReleaseArtProfile
> Task :app:packageRelease
> Task :app:createReleaseApkListingFileRedirect
> Task :app:assembleRelease
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_3-1794293165254.json

BUILD SUCCESSFUL in 52s
53 actionable tasks: 53 executed
----- Signatur -----
V2 Signer: certificate DN: CN=Honey Balecta, OU=Datenfaehre, O=honeybalecta, C=DE
V2 Signer: certificate SHA-256 digest: d5d7fb47399a7fc554b587e2426c00dd5a90966d371098a90a4adff7188b5756
```
