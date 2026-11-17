plugins { id("com.android.application") }

android {
    namespace = "com.cod3uchiha.oneos"
    compileSdk = 36
    ndkVersion = "30.0.16248370"
    defaultConfig {
        applicationId = "com.cod3uchiha.oneos"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0-qemu"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        getByName("main") {
            java.srcDir("../../vendor/QubeVM/qube-android-arm/src/main/java")
            res.srcDir("../../vendor/QubeVM/qube-android-arm/src/main/res")
            assets.srcDir("../../vendor/QubeVM/qube-android-arm/src/main/assets")
            jniLibs.srcDir("src/main/jniLibs")
        }
    }
    buildTypes { release { isMinifyEnabled = false } }
}

dependencies {
    implementation(project(":qube-android-lib"))
    implementation("androidx.appcompat:appcompat:1.7.1")
}
