plugins { id("com.android.library") }
android {
    namespace = "com.wedo.jwimport"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("org.jsoup:jsoup:1.18.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
