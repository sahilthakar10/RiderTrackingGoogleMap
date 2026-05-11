plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("maven-publish")
}

android {
    namespace = "com.codeint.ridertracking"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Google Maps
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)

    // ViewModel Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Retrofit (for Route API)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
}

// Maven publishing for SDK distribution
android.publishing {
    singleVariant("release") {
        withSourcesJar()
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.codeint.ridertracking"
                artifactId = "ridertracking-sdk"
                version = "1.0.0"

                pom {
                    name.set("RiderTracking SDK")
                    description.set("Real-time rider tracking on Google Maps with multi-stop delivery support")
                }
            }
        }
    }
}
