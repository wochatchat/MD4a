plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    namespace = "com.md4a"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)

    implementation(libs.androidx.core.ktx)

    // Markdown parsing: CommonMark spec parser + GFM extensions
    api(libs.commonmark)
    implementation(libs.commonmark.tables)
    implementation(libs.commonmark.strikethrough)
    implementation(libs.commonmark.autolink)
    implementation(libs.commonmark.tasklist)

    // Image rendering inside markdown documents (SVG/GIF for README badges etc.)
    api(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.coil.gif)

    testImplementation(libs.junit)
}

// Publish to Maven so any project (or JitPack: com.github.wochatchat:MD4a) can depend on the SDK.
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = "com.github.wochatchat"
                artifactId = "md4a"
                version = "0.1.0"
                from(components["release"])
            }
        }
    }
}
elease"])
            }
        }
    }
}
