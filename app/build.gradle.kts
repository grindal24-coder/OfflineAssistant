import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Ключ Brave Search API читается из local.properties (файл НЕ в git, см.
// корневой .gitignore), чтобы случайно не закоммитить секрет. Если ключ не
// задан — WebAnswerHandler автоматически откатится на бесключевой
// DuckDuckGo Instant Answer fallback (см. ai/DuckDuckGoInstantAnswerClient.kt).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val braveSearchApiKey: String = localProperties.getProperty("BRAVE_SEARCH_API_KEY") ?: ""

android {
    namespace = "com.offlineassistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.offlineassistant"
        minSdk = 29        // Android 10+, см. п.2 ТЗ
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-mvp"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BRAVE_SEARCH_API_KEY", "\"$braveSearchApiKey\"")

        ndk {
            // Только arm64-v8a — см. п.2 ТЗ (целевое устройство ARM64)
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17 -O3"
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
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
        viewBinding = true
        buildConfig = true
    }

    packaging {
        // На случай конфликтов нативных либ из зависимостей (Vosk/Porcupine и т.п.)
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Room — локальное хранилище (п.13.9 ТЗ)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Wake-word: раскомментируй нужный вариант после регистрации ключа/выбора движка (п.9 ТЗ)
    // implementation("ai.picovoice:porcupine-android:3.0.3")
    // implementation("org.tensorflow:tensorflow-lite:2.16.1")

    // Whisper.cpp подключается как нативная либа через JNI (см. ai/README.md),
    // отдельной gradle-зависимости не требует.

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
