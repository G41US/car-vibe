plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.g41us.carvibe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.g41us.carvibe"
        // The head unit runs Android 8.1 (API 27) under a fake "Android 14" label.
        minSdk = 26
        targetSdk = 34
        // GitHub Actions numbers every build, so each one is newer than the last
        // and the in-app updater can tell. Local builds stay at 1.
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = if (run != null) run + 1 else 1
        versionName = if (run != null) "1.0.${run + 1}" else "1.0.0"
    }

    signingConfigs {
        create("carvibe") {
            // A fixed key so every new build installs over the previous one.
            storeFile = file("carvibe.jks")
            storePassword = "carvibe-launcher"
            keyAlias = "carvibe"
            keyPassword = "carvibe-launcher"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("carvibe")
        }
        debug {
            signingConfig = signingConfigs.getByName("carvibe")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2/")
            it.maxHeapSize = "2g"
        }
    }
}

dependencies {
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Renders the screens off-device so layouts can be checked as PNGs.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
