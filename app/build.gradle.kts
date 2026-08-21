plugins {
    // Kotlin support is built into AGP 9 — the org.jetbrains.kotlin.android
    // plugin must NOT be applied. The Compose plugin is still required.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.podxboq.yahora"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.podxboq.yahora"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Signing the release with the project's own key is what lets F-Droid ship a
    // reproducible build: it compiles this same commit, checks its APK against
    // the published one byte for byte, and then distributes the one signed here.
    // The alternative — letting F-Droid sign — is a door that closes behind you,
    // and it would mean the APK on F-Droid could never be swapped for the one on
    // any other channel without users uninstalling first.
    //
    // The credentials are read from Gradle properties that live outside this
    // repository, on the maintainer's machine. A keystore must never be
    // committed. Where they are absent — F-Droid's buildserver, CI, any clone —
    // the release build produces an unsigned APK instead of failing, which is
    // exactly what F-Droid wants to compare against.
    val releaseKeystore = providers.gradleProperty("YAHORA_KEYSTORE").orNull

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.gradleProperty("YAHORA_KEYSTORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("YAHORA_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("YAHORA_KEY_PASSWORD").get()
            }
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
        // The detail screen groups entries with java.time, which is API 26.
        // Desugaring backports it rather than raising minSdk.
        isCoreLibraryDesugaringEnabled = true
    }

    // Google's dependency metadata block is a Protobuf blob signed by Google
    // that AGP staples into the APK. F-Droid's scanner rejects it: it is not
    // built from source and it is not reproducible. Nothing in the app reads it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        compose = true
        // For BuildConfig.DEBUG, which is what decides whether a tag may be
        // deleted together with its history. Off by default since AGP 8.
        buildConfig = true
    }

    testOptions {
        // Robolectric needs the merged Android resources to run DAO tests on the JVM.
        unitTests.isIncludeAndroidResources = true
    }
}

// FdroidMetadataTest checks the store listing and the version code, and neither
// the fastlane directory nor this build script counts as an input of a test
// task. Without declaring them, dropping a screenshot or overrunning a
// description leaves the task UP-TO-DATE: the check does not run, and the build
// stays green for the wrong reason.
tasks.withType<Test>().configureEach {
    inputs.dir(layout.projectDirectory.dir("../fastlane/metadata/android"))
        .withPropertyName("fdroidListing")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("build.gradle.kts"))
        .withPropertyName("moduleBuildScript")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("../fdroid/com.podxboq.yahora.yml"))
        .withPropertyName("fdroidRecipe")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

kotlin {
    jvmToolchain(21)
}

ksp {
    // Exported schemas make future migrations reviewable in code review.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    coreLibraryDesugaring(libs.android.desugar.jdk.libs)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    // Compose UI tests run on the JVM under Robolectric, so interaction rules
    // stay in the fast suite instead of needing a device.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
