plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("com.google.gms.google-services")
}
android {
 namespace = "com.annukumar.team"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.annukumar.personal"
  minSdk = 23
  targetSdk = 35
  versionCode = 201
  versionName = "2.01-personal"
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
 implementation("com.google.firebase:firebase-auth")
 implementation("com.google.firebase:firebase-firestore")
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.appcompat:appcompat:1.7.0")
 implementation("com.google.android.material:material:1.12.0")
 testImplementation("junit:junit:4.13.2")
}
