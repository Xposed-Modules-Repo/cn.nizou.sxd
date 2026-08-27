import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.lsplugin.resopt)
}

fun String.execute(): String {
    val byteOut = ByteArrayOutputStream()
    project.exec {
        commandLine = this@execute.split("\\s".toRegex())
        standardOutput = byteOut
    }
    return String(byteOut.toByteArray()).trim()
}

android {
    namespace = "cn.nizou.sxd"
    compileSdk = 34

    signingConfigs {
        val jks = file("../keystore.jks")
        if (jks.exists()) {
            register("release") {
                storeFile = jks
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "cn.nizou.sxd"
        minSdk = 27
        targetSdk = 34
        versionCode = 20
        versionName = "1.7.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release") ?: getByName("debug").signingConfig
            versionNameSuffix = runCatching { "-${"git rev-parse --verify --short HEAD".execute()}" }
                .getOrNull() ?: ""
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    androidResources {
        // resopt 保留包 ID：默认 0x23（模块注入宿主时，模块资源不与宿主 0x7f 冲突）。
        // 如需打「可独立启动、使用标准 0x7f 包 ID」的变体，可传
        //   -PresoptPackageId=0x7f   或   -PresoptPackageId=  （空=不启用保留包 ID）
        // 独立启动资源解析失败的排查：0x7f 为应用自身标准包 ID，最稳。
        val resoptPackageId = (project.findProperty("resoptPackageId") as String? ?: "0x23")
        if (resoptPackageId.isNotBlank()) {
            additionalParameters += arrayOf(
                "--allow-reserved-package-id",
                "--package-id",
                resoptPackageId
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    compileOnly(libs.libxposed.api)

    // --- Compose Material3 ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)
}