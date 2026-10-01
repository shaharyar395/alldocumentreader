plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.theoccess.alldocreader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.theoccess.alldocreader"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // In-app document camera (Image to PDF / Scan to PDF)
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.guava:guava:32.1.3-android")
    // PDF editing (reorder / rotate / delete / insert pages, text search, extract)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // Premium subscriptions (Google Play Billing)
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    // Save to Google Drive (Google sign-in + Drive "drive.file" permission)
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    // ads (Google AdMob) + consent form for EEA / UK users
    implementation("com.google.android.gms:play-services-ads:23.3.0")
    implementation("com.google.android.ump:user-messaging-platform:3.0.0")
}
