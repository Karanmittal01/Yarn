import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * Version codes are derived automatically so nobody has to bump them by hand:
 * CI passes YARN_VERSION_CODE (run number); locally we use the git commit count.
 */
fun gitCommitCount(): Int = runCatching {
    val process = ProcessBuilder("git", "rev-list", "--count", "HEAD").directory(rootDir).redirectErrorStream(true).start()
    process.inputStream.bufferedReader().readText().trim().toInt()
}.getOrDefault(1)

val autoVersionCode: Int = System.getenv("YARN_VERSION_CODE")?.toIntOrNull() ?: gitCommitCount()

/** Release signing comes from keystore.properties (local, git-ignored) or CI environment variables. */
val signingProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(key: String, env: String): String? = signingProps.getProperty(key) ?: System.getenv(env)
val releaseStoreFile = signingValue("storeFile", "YARN_KEYSTORE_PATH")

android {
    namespace = "app.yarn"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.yarn.messages"
        minSdk = 29
        targetSdk = 36
        versionCode = autoVersionCode
        versionName = "0.1.$autoVersionCode"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "YARN_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "YARN_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "YARN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Per-ABI APKs for sideloading (./gradlew assembleRelease -PabiSplits). Off by default because
    // App Bundles can't be built with splits enabled; Play splits bundles per device itself.
    splits {
        abi {
            isEnable = project.hasProperty("abiSplits")
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric fetches its Android runtime jars; use Google's Maven Central mirror.
            it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core:intelligence"))
    implementation(project(":core:mms"))
    implementation(project(":core:crypto"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)

    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)
    implementation(libs.biometric)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)
    implementation(libs.coroutines.guava)
    implementation(libs.serialization.json)

    implementation(libs.mlkit.entity.extraction)
    implementation(libs.mlkit.smart.reply)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)
    implementation(libs.mlkit.genai.summarization)
    implementation(libs.mlkit.genai.rewriting)
    implementation(libs.mediapipe.genai)
    implementation(libs.play.app.update)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)
}
