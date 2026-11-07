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

val buildRustEngine by tasks.registering(Exec::class) {
    group = "build"
    description = "Compiles the Rust engine (libwearsic_engine.so) for arm64-v8a."
    workingDir(rustDir)
    // Build environment: PATH with cargo, NDK sysroot for LLD, and the
    // empty libunwind archive (Rust's own libunwind rlib provides symbols).
    environment("PATH", System.getenv("PATH") + ":" + System.getProperty("user.home") + "/.cargo/bin")
    val ndkSysroot = File(sdkDir, "ndk/${android.ndkVersion ?: "27.2.12479018"}/toolchains/llvm/prebuilt/linux-x86_64/sysroot")
    val unwindLibs = File(System.getProperty("user.home"), ".wearsic-link-libs")
    val cargoOutput = File(rustDir, "target/aarch64-linux-android/release/libwearsic_engine.so")
    val abiOutputDir = File(rustJniLibsDir, "arm64-v8a")
    val engineOutput = File(abiOutputDir, "libwearsic_engine.so")
    inputs.dir(File(rustDir, "crates"))
    inputs.file(File(rustDir, "Cargo.toml"))
    outputs.file(engineOutput)

    // Reproduce the working aarch64 host link recipe (see BUILD_INSTRUCTIONS).
    val rustLld = File(System.getProperty("user.home"),
        ".rustup/toolchains/stable-aarch64-unknown-linux-gnu/lib/rustlib/aarch64-unknown-linux-gnu/bin/rust-lld")
    environment("RUSTFLAGS",
        "-C linker=${rustLld.absolutePath} " +
            "-C link-arg=--sysroot=${ndkSysroot.absolutePath} " +
            "-C link-arg=-L${ndkSysroot.absolutePath}/usr/lib/aarch64-linux-android/26 " +
            "-C link-arg=-L${unwindLibs.absolutePath}")
    commandLine("cargo", "build", "--target", "aarch64-linux-android", "--release")

    doLast {
        if (!cargoOutput.exists()) {
            throw GradleException("Rust build did not produce $cargoOutput")
        }
        abiOutputDir.mkdirs()
        cargoOutput.copyTo(engineOutput, overwrite = true)
        println("Rust engine OK: ${engineOutput.absolutePath}")
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

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

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
            useLegacyPackaging = false
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
