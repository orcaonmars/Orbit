import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.hydrogen.ai"
    compileSdk = 35

    defaultConfig {
        // Permanent public ID of the released app. Never change it: phones treat a new ID as a different app,
        // so existing users would stop getting updates. Must match targetPackage in src/main/res/xml/shortcuts.xml.
        applicationId = "io.github.orcaonmars.orbit"
        minSdk = 26
        targetSdk = 35
        // Framework-only instrumentation avoids downloading test dependencies for offline QA.
        testInstrumentationRunner = "com.hydrogen.ai.CredentialStoreInstrumentation"
        // Bump both together, then rewrite model/WhatsNew.kt so the next update shows its own notes.
        versionCode = 3
        versionName = "0.2.1-alpha"
    }

    buildTypes {
        release {
            // R8 removes unused code and resources and optimizes the rest. Names are kept (proguard-rules.pro),
            // so a stack trace from a release build reads the same as the source.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = false
        buildConfig = true
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Development (debug) builds keep the codename ID: the emulator QA scripts and test data use it, and a debug
// build can then sit next to the released app on the same phone. src/debug/res/xml/shortcuts.xml matches it.
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.applicationId.set("com.hydrogen.ai")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

composeCompiler {
    // The Compose mapping file only decodes renamed classes in release stack traces. Orbit keeps its names
    // (proguard-rules.pro), so the file adds nothing, and the tool that writes it is not in the offline build.
    includeComposeMappingFile.set(false)
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
