plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ftcloud.app"
    compileSdk = 35

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("FTCLOUD_RELEASE_STORE_FILE")
                ?: System.getenv("MYVAULT_RELEASE_STORE_FILE")
                ?: System.getenv("KEYSTORE_PATH")
            val envStorePassword = (
                System.getenv("FTCLOUD_RELEASE_STORE_PASSWORD")
                    ?: System.getenv("MYVAULT_RELEASE_STORE_PASSWORD")
                    ?: System.getenv("KEY_STORE_PASSWORD")
                )?.takeIf { it.isNotBlank() }
            val envKeyAlias = (
                System.getenv("FTCLOUD_RELEASE_KEY_ALIAS")
                    ?: System.getenv("MYVAULT_RELEASE_KEY_ALIAS")
                    ?: System.getenv("KEY_ALIAS")
                )?.takeIf { it.isNotBlank() }
            val envKeyPassword = (
                System.getenv("FTCLOUD_RELEASE_KEY_PASSWORD")
                    ?: System.getenv("MYVAULT_RELEASE_KEY_PASSWORD")
                    ?: System.getenv("KEY_PASSWORD")
                )?.takeIf { it.isNotBlank() }

            if (!keystorePath.isNullOrBlank() && file(keystorePath).exists() &&
                envStorePassword != null && envKeyAlias != null
            ) {
                storeFile = file(keystorePath)
                storePassword = envStorePassword
                keyAlias = envKeyAlias
                keyPassword = envKeyPassword ?: envStorePassword
            } else {
                // Permanent deterministic repository keystore (SHA-256: 59:1B:F0:DF:...)
                // Never falls back to AGP's ephemeral ~/.config/.android/debug.keystore
                storeFile = file("signing/ftcloud-signing.p12")
                storeType = "PKCS12"
                storePassword = "ftcloudrelease"
                keyAlias = "ftcloud"
                keyPassword = "ftcloudrelease"
            }
        }
    }

    defaultConfig {
        applicationId = "com.ftcloud.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "1.2.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val unifiedSigning = signingConfigs.getByName("release")

    buildTypes {
        debug {
            signingConfig = unifiedSigning
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = unifiedSigning
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
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        textReport = true
        disable += setOf(
            "GradleDependency",
            "AndroidGradlePluginVersion",
            "NewerVersionAvailable",
        )
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.exifinterface)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.session)

    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    implementation(libs.okhttp)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
