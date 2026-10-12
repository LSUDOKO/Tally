import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Secrets come from app/local.properties (never committed): GROQ_API_KEY, TALLY_RPC_URL.
val local = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localOr(key: String, default: String) = (local.getProperty(key) ?: System.getenv(key) ?: default)

// Release signing comes from ~/.config/tally/release.properties (never committed).
val release = Properties().apply {
    val f = File(System.getProperty("user.home"), ".config/tally/release.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.tally"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.tally"
        minSdk = 28
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"
        manifestPlaceholders["appLabel"] = "Tally"
        buildConfigField("String", "RPC_URL", "\"${localOr("TALLY_RPC_URL", "https://api.devnet.solana.com")}\"")
        buildConfigField("String", "GROQ_API_KEY", "\"${localOr("GROQ_API_KEY", "")}\"")
    }

    signingConfigs {
        if (release.getProperty("storeFile") != null) {
            create("release") {
                storeFile = File(release.getProperty("storeFile"))
                storePassword = release.getProperty("storePassword")
                keyAlias = release.getProperty("keyAlias")
                keyPassword = release.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("Boolean", "DEMO_OWNER_ALLOWED", "true")
            // Allows a QA build alongside an existing app signed with a different key.
            if (providers.gradleProperty("tallyQaInstall").isPresent) {
                applicationIdSuffix = ".qa"
                manifestPlaceholders["appLabel"] = "Tally QA"
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("Boolean", "DEMO_OWNER_ALLOWED", "false")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/{AL2.0,LGPL2.1}")
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.mwa.clientlib.ktx)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.okhttp)
    implementation(libs.zxing.core)
    implementation(libs.serialization.json)
    implementation(libs.serialization.cbor)
    implementation(libs.bouncycastle)
    implementation(libs.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
}
