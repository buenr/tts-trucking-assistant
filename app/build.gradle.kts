import java.util.Properties
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Load local.properties at the top level
val localProperties = Properties()
val localPropertiesFile = project.rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { stream ->
        localProperties.load(stream)
    }
}

fun getLocalProperty(key: String, default: String): String {
    val value = localProperties.getProperty(key) ?: default
    return value.trim().removeSurrounding("\"").removeSurrounding("'")
}

// Note: Configure secrets in local.properties:
// VERTEX_AI_PROJECT_ID=your-project-id
// VERTEX_AI_LOCATION=global
// VERTEX_AI_MODEL=gemini-2.5-flash

android {
    namespace = "trucker.geminiflash"
    compileSdk = 35

    defaultConfig {
        applicationId = "trucker.geminiflash"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        fun toBase64(value: String): String {
            return Base64.getEncoder().encodeToString(value.toByteArray())
        }

        buildConfigField("String", "VERTEX_AI_PROJECT_ID", "\"${getLocalProperty("VERTEX_AI_PROJECT_ID", "")}\"")
        buildConfigField("String", "VERTEX_AI_LOCATION", "\"${getLocalProperty("VERTEX_AI_LOCATION", "global")}\"")
        buildConfigField("String", "VERTEX_AI_MODEL", "\"${getLocalProperty("VERTEX_AI_MODEL", "gemini-2.5-flash-preview-04-09")}\"")

        // Encrypted credential store fields (placeholder — auth goes through VertexCredentialsManager/assets)
        buildConfigField("String", "ENCRYPTED_VERTEX_KEY", "\"${getLocalProperty("ENCRYPTED_VERTEX_KEY", "PLACEHOLDER")}\"")
        buildConfigField("String", "BUILD_KEY_FRAGMENT", "\"${getLocalProperty("BUILD_KEY_FRAGMENT", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
    
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.google.auth.library.oauth2.http)
    implementation(libs.google.genai)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
