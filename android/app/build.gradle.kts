import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val dfVersion = providers.environmentVariable("DF_VERSION").getOrElse("1.0.0").removePrefix("v")
val dfCode = providers.environmentVariable("DF_VERSION_CODE").getOrElse("1").toInt()

android {
    namespace = "de.honeybalecta.datenfaehre"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.honeybalecta.datenfaehre"
        minSdk = 29
        targetSdk = 36
        versionCode = dfCode
        versionName = dfVersion
    }

    // Fester Schlüssel im Repo (PKCS12, passwortgeschützt); das Passwort kommt aus dem Secret
    // ANDROID_KEYSTORE_PASSWORD. Nur so lassen sich Updates über die installierte Version spielen.
    signingConfigs {
        create("release") {
            val pw = providers.environmentVariable("DF_KEYSTORE_PASSWORD").orNull
            if (pw != null) {
                storeFile = file("../keystore/release.p12")
                storeType = "pkcs12"
                storePassword = pw
                keyAlias = "datenfaehre"
                keyPassword = pw
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources {
            excludes += listOf(
                "META-INF/*.kotlin_module", "META-INF/*.version", "META-INF/versions/**",
                "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST",
                "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA"
            )
        }
    }
    lint {
        // Seitenlade-App ohne Play Store: voller Dateizugriff und eigene Zertifikatsprüfung sind gewollt
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // FTP und FTP über TLS
    implementation("commons-net:commons-net:3.11.1")
    // SFTP (gepflegter JSch-Nachfolger), Bouncy Castle für Ed25519- und Curve25519-Schlüssel
    implementation("com.github.mwiede:jsch:0.2.23")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    testImplementation("junit:junit:4.13.2")
}
