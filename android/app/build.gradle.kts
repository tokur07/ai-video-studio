plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val storeFileProp = providers.gradleProperty("STORE_FILE").orNull
val storePasswordProp = providers.gradleProperty("STORE_PASSWORD").orNull
val keyAliasProp = providers.gradleProperty("KEY_ALIAS").orNull
val keyPasswordProp = providers.gradleProperty("KEY_PASSWORD").orNull

val resolvedKeystoreFile = storeFileProp
    ?.takeIf { it.isNotBlank() }
    ?.let { rootProject.file(it) }

val hasValidSigning = resolvedKeystoreFile != null &&
    resolvedKeystoreFile.exists() &&
    !storePasswordProp.isNullOrBlank() &&
    !keyAliasProp.isNullOrBlank() &&
    !keyPasswordProp.isNullOrBlank()

android {
    namespace = "com.aivideostudio.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aivideostudio.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        if (hasValidSigning) {
            create("persistentDebug") {
                storeFile = resolvedKeystoreFile
                storePassword = storePasswordProp
                keyAlias = keyAliasProp
                keyPassword = keyPasswordProp
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            if (hasValidSigning) {
                signingConfig = signingConfigs.getByName("persistentDebug")
            }
        }
        release {
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
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
