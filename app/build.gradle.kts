import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Load .env properties from root or app directory
val envProps = Properties().apply {
    val envFile = rootProject.file(".env").takeIf { it.exists() } ?: project.file(".env").takeIf { it.exists() }
    envFile?.inputStream()?.use { load(it) }
}

fun getEnv(key: String, fallback: String = ""): String {
    return envProps.getProperty(key)?.trim() ?: System.getenv(key) ?: fallback
}

android {
    namespace = "com.nagabyte.scalpx"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nagabyte.scalpx"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        manifestPlaceholders["authDeepLinkScheme"] = "scalper"
        manifestPlaceholders["authDeepLinkHost"] = "auth"

        buildConfigField("String", "ENV_BASE_URL", "\"${getEnv("SCALPX_BASE_URL", "http://localhost:3000")}\"")
        buildConfigField("String", "ENV_JWT_TOKEN", "\"${getEnv("SCALPX_JWT_TOKEN", "")}\"")
        buildConfigField("String", "ENV_FLATTRADE_USER_ID", "\"${getEnv("FLATTRADE_USER_ID", "")}\"")
        buildConfigField("String", "ENV_FLATTRADE_PASSWORD", "\"${getEnv("FLATTRADE_PASSWORD", "")}\"")
        buildConfigField("String", "ENV_FLATTRADE_ACCESS_TOKEN", "\"${getEnv("FLATTRADE_ACCESS_TOKEN", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
        freeCompilerArgs = listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Jetpack Compose BOM 2024.11.00
    val composeBom = platform("androidx.compose:compose-bom:2024.11.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // AndroidX Core & Lifecycle
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Security & Auth
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // Coroutines & Immutable Collections
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.8")

    // Networking: OkHttp & Retrofit + Kotlinx Serialization
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
