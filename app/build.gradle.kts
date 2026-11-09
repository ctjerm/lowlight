import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val uploadKeystorePath = providers.environmentVariable("LOWLIGHT_UPLOAD_KEYSTORE").orNull
val uploadKeystorePassword = providers.environmentVariable("LOWLIGHT_UPLOAD_STORE_PASSWORD").orNull
val uploadKeyAlias = providers.environmentVariable("LOWLIGHT_UPLOAD_KEY_ALIAS").orNull
val uploadKeyPassword = providers.environmentVariable("LOWLIGHT_UPLOAD_KEY_PASSWORD").orNull
val hasUploadSigning = listOf(
    uploadKeystorePath,
    uploadKeystorePassword,
    uploadKeyAlias,
    uploadKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "io.github.ctjerm.lowlight"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.ctjerm.lowlight"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasUploadSigning) {
            create("upload") {
                storeFile = file(checkNotNull(uploadKeystorePath))
                storePassword = checkNotNull(uploadKeystorePassword)
                keyAlias = checkNotNull(uploadKeyAlias)
                keyPassword = checkNotNull(uploadKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("upload")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // F-Droid rejects this signing block because only Google can decrypt it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/LICENSE.md",
            "/META-INF/LICENSE-notice.md",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.foundation:foundation:1.10.2")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.ui:ui:1.10.2")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.2")
    implementation("androidx.core:core-ktx:1.15.0")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling:1.10.2")
}
