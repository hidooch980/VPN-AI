plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val releaseStoreFile = System.getenv("ANDROID_KEYSTORE_FILE")
val releaseStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
val releaseSigningReady = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
).all { !it.isNullOrBlank() }

val releasePackagingRequested = gradle.startParameter.taskNames.any {
    it.contains("assembleRelease", ignoreCase = true) ||
        it.contains("bundleRelease", ignoreCase = true)
}
if (releasePackagingRequested && !releaseSigningReady) {
    throw GradleException(
        "Stable release packaging is blocked. Configure the permanent release signing identity first."
    )
}

android {
    namespace = "ai.vpnai.client"
    compileSdk = 35
    defaultConfig {
        applicationId = "ai.vpnai.client"
        minSdk = 26
        targetSdk = 35
        versionCode = ciRunNumber
        versionName = "1.0.$ciRunNumber"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

androidComponents {
    beforeVariants(selector().withBuildType("debug")) { variantBuilder ->
        variantBuilder.enable = false
    }
}

dependencies {
    implementation("com.wireguard.android:tunnel:1.0.20260102")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    testImplementation("junit:junit:4.13.2")
}
