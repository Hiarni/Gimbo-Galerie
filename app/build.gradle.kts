import java.util.Properties

// Plugins
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Robolectric-SDKs für Unit-Tests
val robolectricSdk29 by configurations.creating
val robolectricSdk35 by configurations.creating
val prepareRobolectricSdk by tasks.registering(Copy::class) {
    from(robolectricSdk29, robolectricSdk35)
    into(layout.buildDirectory.dir("robolectric-sdk"))
}

// Lizenztexte für die App
val licenseAssets = layout.buildDirectory.dir("generated/licenseAssets")
val copyLicenseAssets by tasks.registering(Copy::class) {
    from(rootProject.file("LICENSE"), rootProject.file("THIRD-PARTY-NOTICES.txt"))
    from(rootProject.file("LICENSES")) { into("LICENSES") }
    into(licenseAssets.map { it.dir("licenses") })
}
tasks.named("preBuild") { dependsOn(copyLicenseAssets) }

// Upload-Signierung (signing.properties)
val uploadSigning = Properties().apply {
    val config = rootProject.file("signing.properties")
    if (config.isFile) config.reader(Charsets.UTF_8).use { load(it) }
}
val hasUploadSigning = uploadSigning.isNotEmpty()
if (hasUploadSigning) {
    require(listOf("store.file", "store.password", "key.alias", "key.password").all { !uploadSigning.getProperty(it).isNullOrBlank() }) {
        "Incomplete signing.properties configuration"
    }
    require(rootProject.file(uploadSigning.getProperty("store.file")).isFile) { "Upload keystore file is missing" }
}

// Android
android {
    namespace = "de.luma.gallery"
    compileSdk = 36

    // Standardkonfiguration
    defaultConfig {
        applicationId = "de.luma.gallery"
        minSdk = 29
        targetSdk = 36
        versionCode = 94
        versionName = "0.94 Beta"
        resourceConfigurations += listOf("en", "de", "es", "fr", "it", "pt", "nl", "pl", "cs", "sk", "hu", "ro", "el", "tr", "ru", "uk", "sv", "da", "nb", "fi", "ar", "he", "fa", "hi", "bn", "id", "ms", "vi", "th", "ja", "ko", "b+zh+Hans", "b+zh+Hant")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }

    // Quellen und Tests
    bundle { language { enableSplit = false } }
    sourceSets.getByName("main").assets.srcDir(licenseAssets)
    sourceSets.getByName("test").resources.srcDir("schemas")
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.dependsOn(prepareRobolectricSdk)
            test.systemProperty("robolectric.dependency.dir", layout.buildDirectory.dir("robolectric-sdk").get().asFile.absolutePath)
        }
    }
    // Java und Kotlin
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // Signierung
    if (hasUploadSigning) signingConfigs.create("upload") {
        storeFile = rootProject.file(uploadSigning.getProperty("store.file"))
        storePassword = uploadSigning.getProperty("store.password")
        keyAlias = uploadSigning.getProperty("key.alias")
        keyPassword = uploadSigning.getProperty("key.password")
    }
    // Build-Typen
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasUploadSigning) signingConfig = signingConfigs.getByName("upload")
        }
    }
}

// Room
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// Abhängigkeiten
dependencies {
    // Compose und AndroidX
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.core:core-ktx:1.16.0")

    // Bilder und Medien
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
    implementation("androidx.media3:media3-transformer:1.9.0")
    implementation("androidx.media3:media3-effect:1.9.0")
    implementation("androidx.media3:media3-exoplayer:1.9.0")
    implementation("androidx.media3:media3-ui:1.9.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")

    // Hintergrundaufgaben
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // Speicherung
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    robolectricSdk29("org.robolectric:android-all-instrumented:10-robolectric-5803371-i7")
    robolectricSdk35("org.robolectric:android-all-instrumented:15-robolectric-12650502-i7")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
