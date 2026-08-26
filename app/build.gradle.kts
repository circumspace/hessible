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
        versionName = providers.environmentVariable("HESSIBLE_VERSION_NAME")
            .orNull ?: "0.1.0"
    }

    val signingProperties = providers.environmentVariable("HESSIBLE_KEYSTORE_FILE")
        .zip(providers.environmentVariable("HESSIBLE_KEYSTORE_PASSWORD")) { file, password -> file to password }
        .zip(providers.environmentVariable("HESSIBLE_KEY_ALIAS")) { (file, password), alias -> Triple(file, password, alias) }
        .zip(providers.environmentVariable("HESSIBLE_KEY_PASSWORD")) { (file, password, alias), keyPassword ->
            arrayOf(file, password, alias, keyPassword)
        }
    if (signingProperties.isPresent) {
        signingConfigs.create("release") {
            val (file, password, alias, keyPassword) = signingProperties.get()
            storeFile = rootProject.file(file)
            storePassword = password
            keyAlias = alias
            this.keyPassword = keyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
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
