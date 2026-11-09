import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ---------------------------------------------------------------------------
// Rust engine build (Phase 2 foundation)
// ---------------------------------------------------------------------------
// Builds the Rust engine to lib/arm64-v8a/libwearsic_engine.so via
// cargo-ndk. Requires: rustup target aarch64-linux-android, cargo-ndk
// installed, and an NDK matching `ndkVersion` below.
// ---------------------------------------------------------------------------
val rustDir: File = rootProject.layout.projectDirectory.dir("rust").asFile
val rustJniLibsDir: File = layout.buildDirectory.dir("rust-jniLibs").get().asFile

val sdkDir: File = run {
    val localProps = Properties()
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        f.inputStream().use { localProps.load(it) }
    }
    (localProps.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME"))?.let {
        File(it)
    } ?: file("/root/android-sdk")
}

val rustCargoPath = System.getProperty("user.home") + "/.cargo/bin"
val rustLldPath = File(
    System.getProperty("user.home"),
    ".rustup/toolchains/stable-aarch64-unknown-linux-gnu/lib/rustlib/aarch64-unknown-linux-gnu/bin/rust-lld",
).absolutePath
val ndkSysrootPath = File(
    sdkDir,
    "ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/sysroot",
).absolutePath
val unwindLibsPath = File(System.getProperty("user.home"), ".wearsic-link-libs").absolutePath

// ---------------------------------------------------------------------------
// Release signing (credentials are never committed)
// ---------------------------------------------------------------------------
// Resolution order per value:
//   1. environment: WEARSIC_KEYSTORE_FILE / _PASSWORD / _KEY_ALIAS / _KEY_PASSWORD
//   2. keystore.properties in the project root (gitignored)
// When nothing is configured the release APK is simply left unsigned, so
// `assembleRelease` still works for R8/lint verification on a fresh clone.
// ---------------------------------------------------------------------------
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties")
        .takeIf { it.exists() }
        ?.inputStream()
        ?.use { load(it) }
}

fun signingValue(envKey: String, propKey: String): String? =
    System.getenv(envKey) ?: keystoreProps.getProperty(propKey)

val keystoreFile: File? = signingValue("WEARSIC_KEYSTORE_FILE", "storeFile")
    ?.let { rootProject.file(it) }
    ?.takeIf { it.exists() }
val keystorePassword: String? = signingValue("WEARSIC_KEYSTORE_PASSWORD", "storePassword")
val keystoreAlias: String? = signingValue("WEARSIC_KEY_ALIAS", "keyAlias")
val keystoreKeyPassword: String? = signingValue("WEARSIC_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning =
    keystoreFile != null && keystorePassword != null &&
        keystoreAlias != null && keystoreKeyPassword != null

val buildRustEngineArm64 by tasks.registering(Exec::class) {
    group = "build"
    workingDir(rustDir)
    environment("PATH", System.getenv("PATH") + ":" + rustCargoPath)
    environment(
        "RUSTFLAGS",
        "-C linker=$rustLldPath " +
            "-C link-arg=--sysroot=$ndkSysrootPath " +
            "-C link-arg=-L$ndkSysrootPath/usr/lib/aarch64-linux-android/26 " +
            "-C link-arg=-L$unwindLibsPath",
    )
    commandLine("cargo", "build", "--target", "aarch64-linux-android", "--release")
}

val buildRustEngineArmv7 by tasks.registering(Exec::class) {
    group = "build"
    workingDir(rustDir)
    environment("PATH", System.getenv("PATH") + ":" + rustCargoPath)
    environment(
        "CC_armv7_linux_androideabi",
        "/data/data/com.termux/files/usr/bin/clang --target=armv7a-linux-androideabi26 -march=armv7-a",
    )
    environment("AR_armv7_linux_androideabi", "/data/data/com.termux/files/usr/bin/llvm-ar")
    environment(
        "CFLAGS_armv7_linux_androideabi",
        "-march=armv7-a -D__ANDROID_API__=26 -D__ANDROID_MIN_SDK_VERSION__=26",
    )
    environment(
        "RUSTFLAGS",
        "-C linker=$rustLldPath " +
            "-C link-arg=--sysroot=$ndkSysrootPath " +
            "-C link-arg=-L$ndkSysrootPath/usr/lib/arm-linux-androideabi/26 " +
            "-C link-arg=-L$unwindLibsPath",
    )
    commandLine("cargo", "build", "--target", "armv7-linux-androideabi", "--release")
}

val buildRustEngine by tasks.registering {
    group = "build"
    description = "Builds the Rust engine for arm64-v8a + armeabi-v7a and stages jniLibs."
    dependsOn(buildRustEngineArm64, buildRustEngineArmv7)
    val abiOutputDir = rustJniLibsDir
    val arm64Out = File(abiOutputDir, "arm64-v8a/libwearsic_engine.so")
    val armv7Out = File(abiOutputDir, "armeabi-v7a/libwearsic_engine.so")
    inputs.dir(File(rustDir, "crates"))
    inputs.file(File(rustDir, "Cargo.toml"))
    outputs.file(arm64Out)
    outputs.file(armv7Out)
    doLast {
        val cargoArm64 = File(rustDir, "target/aarch64-linux-android/release/libwearsic_engine.so")
        if (!cargoArm64.exists()) throw GradleException("arm64 Rust build produced no .so")
        File(abiOutputDir, "arm64-v8a").mkdirs()
        cargoArm64.copyTo(arm64Out, overwrite = true)

        val cargoArmv7 = File(rustDir, "target/armv7-linux-androideabi/release/libwearsic_engine.so")
        if (!cargoArmv7.exists()) throw GradleException("armv7 Rust build produced no .so")
        File(abiOutputDir, "armeabi-v7a").mkdirs()
        cargoArmv7.copyTo(armv7Out, overwrite = true)
        println("Rust engines OK: ${arm64Out.absolutePath}, ${armv7Out.absolutePath}")
    }
}

android {
    namespace = "com.wearsic.next"
    compileSdk {
        version = release(36) { minorApiLevel = 1 }
    }
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.wearsic.next"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = keystoreAlias
                keyPassword = keystoreKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    experimentalProperties["android.experimental.artProfile"] = "off"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(rustJniLibsDir)
        }
    }

    packaging {
        jniLibs {
            // The Rust release build already strips symbols; keep debug
            // symbols here so AGP doesn't invoke the NDK's llvm-strip
            // (x86_64-only binary, unavailable on aarch64 hosts).
            keepDebugSymbols += "**/*.so"
            // Legacy packaging keeps libs inline (AGP 9.1.1 arm64 quirk
            // with the manifest when useLegacyPackaging=false).
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)

    // Media3 — declared now, playback arrives in a later phase.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.wear.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
}

tasks.named("preBuild") {
    dependsOn(buildRustEngine)
}
