import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.juca.hydroponic"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.juca.hydroponic"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        getByName("release") { buildConfigField("String", "API_ORIGIN", "\"https://ppossak-api.jucadrive.com\"") }
        getByName("debug") {
            // Only isolated loopback acceptance can override the production origin at build time.
            val testOrigin = providers.gradleProperty("acceptanceOrigin").orNull
            if (testOrigin != null) {
                val uri = URI(testOrigin)
                require(uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port in 1..65535 &&
                    uri.rawPath.isNullOrEmpty() && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null)
            }
            buildConfigField("String", "API_ORIGIN", "\"${testOrigin ?: "https://ppossak-api.jucadrive.com"}\"")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isReturnDefaultValues = false }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
dependencies {
    val compose = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(compose)
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation(compose)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // Espresso 3.7 replaces the removed reflective InputManager API used by Android 17 devices.
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
