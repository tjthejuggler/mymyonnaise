plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
}

android {
    namespace = "it.ncorti.emgvisualizer"
    compileSdk = 33

    buildFeatures {
        viewBinding = true
    }

    defaultConfig {
        applicationId = "it.ncorti.emgvisualizer"
        minSdk = 21
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")

    implementation("androidx.preference:preference-ktx:1.1.1")


    // Update Dagger dependencies
    implementation("com.google.dagger:dagger:2.44")
    implementation("com.google.dagger:dagger-android:2.44")
    implementation("com.google.dagger:dagger-android-support:2.44")
    kapt("com.google.dagger:dagger-compiler:2.44")
    kapt("com.google.dagger:dagger-android-processor:2.44")

    // AppIntro
    implementation("com.github.AppIntro:AppIntro:6.1.0")

    // Myonnaise
    //implementation("com.ncorti:myonnaise:1.0.0")
    implementation(project(":myonnaise"))

    // RxJava
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")

    // MPAndroidChart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Mockito Kotlin
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")

    implementation("com.github.QuadFlask:colorpicker:0.0.15")


}

