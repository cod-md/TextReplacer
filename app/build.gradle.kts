plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.module"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.module"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        checkReleaseBuilds = false
    }

    dependenciesInfo {
        includeInApk = false
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    implementation("androidx.appcompat:appcompat:1.8.0")

    // Needed by MainActivity to communicate with LSPosed
    implementation("io.github.libxposed:service:102.0.0")

    // Xposed API used by your module
    compileOnly("io.github.libxposed:api:102.0.0")

    compileOnly("androidx.annotation:annotation:1.9.1")
}
