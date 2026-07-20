import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.circumspace.contactstr"
    // Compose 1.12.x (pulled in by material3 1.5.0-alpha) requires compiling against API 37.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.circumspace.contactstr"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Sign release with the debug key so it's installable for performance testing.
            // (Replace with a real release keystore before publishing.)
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// AGP 9 built-in Kotlin: configure the Kotlin toolchain via the top-level kotlin {} extension.
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Nostr: keys, NIP-19 (npub/nsec), NIP-44 encryption, signers.
    implementation(libs.quartz)

    // Image loading (contact photos + remote profile avatars).
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Relay transport.
    implementation(libs.okhttp)

    // vCard import/export.
    implementation(libs.ezvcard)

    // QR contact sharing: generation (core) + camera scanning (embedded).
    implementation(libs.zxing.core)
    implementation(libs.zxing.embedded)

    // EXIF orientation for picked contact photos.
    implementation(libs.androidx.exifinterface)

    // Interactive crop/zoom UI for contact photos.
    implementation(libs.image.cropper)

    testImplementation(libs.junit)
}
