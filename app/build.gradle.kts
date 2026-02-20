plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "xyz.mulin.tvauto"
    compileSdk = 35

    defaultConfig {
        applicationId = "mulin.tvauto.pro"
        minSdk = 19
        targetSdk = 35
        versionCode = 50
        versionName = "5.0"

        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
}
dependencies {
    implementation(libs.material.v190)
    implementation(libs.appcompat)
    implementation(libs.material)

    implementation("androidx.multidex:multidex:2.0.1")

    implementation("org.videolan.android:libvlc-all:3.5.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}