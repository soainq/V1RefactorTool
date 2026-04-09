plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.abc.def.main"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.abc.def.main"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
