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
        versionCode = 10025
        versionName = "1.0.25"

        val mapsApiKey = run {
            val gsFile = file("google-services.json")
            if (gsFile.exists()) {
                val content = gsFile.readText()
                val regex = """"current_key"\s*:\s*"([^"]+)"""".toRegex()
                regex.find(content)?.groupValues?.get(1) ?: ""
            } else {
                ""
            }
        }
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
                    ?: "DqkbM7cUFha7iyIJqi1S6JPoBoupN87vA9#"
                keyAlias = props.getProperty("keyAlias")
                    ?: props.getProperty("RELEASE_KEY_ALIAS")
                    ?: (project.findProperty("MYAPP_RELEASE_KEY_ALIAS") as String?)
                    ?: System.getenv("RELEASE_KEY_ALIAS")
                    ?: "fleetx-pilot"
                keyPassword = props.getProperty("keyPassword")
                    ?: props.getProperty("RELEASE_KEY_PASSWORD")
                    ?: (project.findProperty("MYAPP_RELEASE_KEY_PASSWORD") as String?)
                    ?: System.getenv("RELEASE_KEY_PASSWORD")
                    ?: "DqkbM7cUFha7iyIJqi1S6JPoBoupN87vA9#"
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
            signingConfig = if (releaseConfig.storeFile != null) {
                releaseConfig
            } else {
                throw GradleException("FATAL: Release keystore file not found! Release APKs must be signed with the production FleetX key.")
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
