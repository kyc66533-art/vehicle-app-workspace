plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    useLibrary("android.car")
    namespace = "com.nextdrive.pleos"
    compileSdk = 34
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.nextdrive.pleos"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.nextdrive.pleos.MvpScenarioInstrumentation"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // Public ErrorCode is published as runtime-only by Vehicle.
    // Vehicle already packages this at runtime; expose only its public constants to the compiler.
    compileOnly("ai.pleos.playground:vehicle-data:1.1.11") { isTransitive = false }
}
