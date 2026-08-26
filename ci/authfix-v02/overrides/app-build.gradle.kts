plugins {
    id("com.android.application")
}

android {
    namespace = "com.betawithgamma.microstructure"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.betawithgamma.microstructure"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-authfix"

        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
}
