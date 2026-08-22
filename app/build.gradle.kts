plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "build.bytes.romshifter"
    compileSdk = 37

    defaultConfig {
        applicationId = "build.bytes.romshifter"
        minSdk = 26
        targetSdk = 37
        versionCode = project.findProperty("VERSION_CODE")?.toString()?.toIntOrNull() ?: try {
            project.providers.exec {
                commandLine("git", "rev-list", "--count", "HEAD")
                isIgnoreExitValue = true
            }.standardOutput.asText.map { it.trim().toIntOrNull() ?: 10 }.getOrElse(86)
        } catch (_: Exception) {
            10
        }
        versionName = "Eevee"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = file("release.keystore")
            if (System.getenv("KEYSTORE_PASSWORD") != null) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
        }
    }

    splits {
        abi {
            isEnable = System.getenv("GITHUB_ACTIONS") == "true"
            reset()
            isUniversalApk = false
            include("armeabi-v7a", "arm64-v8a")
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
    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols.add("**/libzapdos.so")
        }
    }
}

dependencies {
    implementation(libs.libsu.core)
    implementation(libs.libsu.io)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.graphics.path)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}