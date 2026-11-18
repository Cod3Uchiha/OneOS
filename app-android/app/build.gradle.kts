plugins { id("com.android.application") }

android {
    namespace = "com.cod3uchiha.oneos"
    compileSdk = 36
    ndkVersion = "30.0.16248370"
    defaultConfig {
        applicationId = "com.cod3uchiha.oneos"
        minSdk = 30
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0-standalone"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDir("src/main/jniLibs")
        }
    }
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
    buildTypes { release { isMinifyEnabled = false } }
}
