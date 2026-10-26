plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun signingValue(name: String): String? =
    (findProperty(name) as String?)?.takeIf { it.isNotBlank() } ?: System.getenv(name)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.littlelock.app"
    compileSdk = 36

    // Release signing comes from outside the repo: environment variables (CI, from GitHub secrets) or Gradle
    // properties (~/.gradle/gradle.properties locally). Without them a release build is debug-signed - fine for a
    // test install, but it won't update an install signed with the real key.
    signingConfigs {
        val storePath = signingValue("LITTLELOCK_KEYSTORE_PATH")
        if (storePath != null && file(storePath).exists()) {
            create("release") {
                storeFile = file(storePath)
                storePassword = signingValue("LITTLELOCK_STORE_PASSWORD")
                keyAlias = signingValue("LITTLELOCK_KEY_ALIAS") ?: "littlelock"
                keyPassword = signingValue("LITTLELOCK_KEY_PASSWORD") ?: signingValue("LITTLELOCK_STORE_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.littlelock.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 19
        versionName = "1.9.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug").also {
                logger.warn("LittleLock: no release keystore (LITTLELOCK_KEYSTORE_PATH) - the release APK is debug-signed")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
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
        viewBinding = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "1g"
                it.maxParallelForks = 1
            }
        }
    }

    applicationVariants.all {
        if (name == "release") {
            outputs.forEach { output ->
                (output as com.android.build.gradle.internal.api.ApkVariantOutputImpl).outputFileName = "LittleLock-release.apk"
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("androidx.preference:preference-ktx:1.2.1")

    // OkHttp for Discord webhook
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
