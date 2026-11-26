import java.util.Properties
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.college.bustracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.college.bustracker"
        minSdk = 26
        targetSdk = 34
        versionCode = 10047
        versionName = "1.0.47"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        fun extractMapsApiKey(gsFile: File): String {
            if (!gsFile.exists()) return ""
            val regex = """"current_key"\s*:\s*"([^"]+)"""".toRegex()
            return regex.find(gsFile.readText())?.groupValues?.get(1) ?: ""
        }

        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            resValue("string", "app_name", "FleetX Staging")
            buildConfigField("String", "FIREBASE_ENV", "\"staging\"")
            buildConfigField("String", "EXPECTED_FIREBASE_PROJECT_ID", "\"college-bus-tracker-staging\"")
            manifestPlaceholders["MAPS_API_KEY"] = extractMapsApiKey(file("src/staging/google-services.json"))
        }

        create("prod") {
            dimension = "environment"
            buildConfigField("String", "FIREBASE_ENV", "\"prod\"")
            buildConfigField("String", "EXPECTED_FIREBASE_PROJECT_ID", "\"college-bus-tracker-prod\"")
            val prodGs = file("src/prod/google-services.json").takeIf { it.exists() } ?: file("google-services.json")
            manifestPlaceholders["MAPS_API_KEY"] = extractMapsApiKey(prodGs)
        }
    }

    lint {
        disable += "InvalidFragmentVersionForActivityResult"
        checkReleaseBuilds = false
        abortOnError = false
    }

    signingConfigs {
        create("release") {
            val bundledKeystore = file("keystore/fleetx_release.jks")
            val keystorePropFile = rootProject.file("keystore.properties").takeIf { it.exists() }
                ?: File(System.getProperty("user.home"), ".secrets/keystore.properties").takeIf { it.exists() }

            val props = Properties()
            if (keystorePropFile != null && keystorePropFile.exists()) {
                keystorePropFile.inputStream().use { props.load(it) }
            }

            val keystorePath = props.getProperty("storeFile")
                ?: props.getProperty("RELEASE_STORE_FILE")
                ?: (project.findProperty("MYAPP_RELEASE_STORE_FILE") as String?)
                ?: System.getenv("RELEASE_STORE_FILE")

            val targetStoreFile = when {
                keystorePath != null && file(keystorePath).exists() -> file(keystorePath)
                bundledKeystore.exists() -> bundledKeystore
                else -> null
            }

            if (targetStoreFile != null) {
                storeFile = targetStoreFile
                storePassword = props.getProperty("storePassword")
                    ?: props.getProperty("RELEASE_STORE_PASSWORD")
                    ?: (project.findProperty("MYAPP_RELEASE_STORE_PASSWORD") as String?)
                    ?: System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = props.getProperty("keyAlias")
                    ?: props.getProperty("RELEASE_KEY_ALIAS")
                    ?: (project.findProperty("MYAPP_RELEASE_KEY_ALIAS") as String?)
                    ?: System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = props.getProperty("keyPassword")
                    ?: props.getProperty("RELEASE_KEY_PASSWORD")
                    ?: (project.findProperty("MYAPP_RELEASE_KEY_PASSWORD") as String?)
                    ?: System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseConfig = signingConfigs.getByName("release")
            val hasSigningCreds = releaseConfig.storeFile != null &&
                !releaseConfig.storePassword.isNullOrBlank() &&
                !releaseConfig.keyAlias.isNullOrBlank() &&
                !releaseConfig.keyPassword.isNullOrBlank()
            signingConfig = if (hasSigningCreds) {
                releaseConfig
            } else {
                throw GradleException("FATAL: Release signing keystore or credentials missing! Provide keystore.properties, ~/.secrets/keystore.properties, or RELEASE_STORE_* env vars.")
            }
        }
        debug {
            isMinifyEnabled = false
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
    testOptions {
        unitTests {
            isIncludeAndroidResources = false
            isReturnDefaultValues = true
            all {
                it.jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
                it.systemProperty("file.encoding", "UTF-8")
                it.systemProperty("sun.jnu.encoding", "UTF-8")
                it.testLogging {
                    events("passed", "skipped", "failed", "standardOut", "standardError")
                }
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Firebase (Import BOM)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.functions.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.appcheck.ktx)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    "stagingImplementation"(libs.firebase.appcheck.debug)

    // Location & Maps
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)
    implementation(libs.osmdroid.android)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.guava)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
