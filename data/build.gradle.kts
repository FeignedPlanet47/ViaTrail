plugins { id("com.android.library"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.kapt"); id("com.google.dagger.hilt.android") }
android { namespace = "com.viatrail.data"; compileSdk = 36
    defaultConfig { minSdk = 26; buildConfigField("String", "GEOAPIFY_KEY", "\"" + (project.findProperty("GEOAPIFY_KEY") as String? ?: "") + "\"") }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
kapt { correctErrorTypes = true }
dependencies {
    implementation(project(":domain"))
    implementation("com.google.dagger:hilt-android:2.56.2")
    kapt("com.google.dagger:hilt-compiler:2.56.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    kapt("androidx.room:room-compiler:2.7.2")
}
