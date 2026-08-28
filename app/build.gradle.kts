import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.lsplugin.resopt)
}

fun gitShortHash(): String = providers.exec {
    commandLine("git", "rev-parse", "--short=8", "HEAD")
}.standardOutput.asText.get().trim()

android {
    namespace = "cn.nizou.sxd"
    compileSdk = 37

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
        minSdk = 33
        targetSdk = 37
        versionCode = 20
        versionName = "1.7.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 构建时间（对齐 WeKit BuildConfig.BUILD_TIMESTAMP，首页设备信息区显示）
        buildConfigField("long", "BUILD_TIMESTAMP", "${System.currentTimeMillis()}L")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release") ?: getByName("debug").signingConfig
            versionNameSuffix = runCatching { "-${gitShortHash()}" }.getOrNull() ?: ""
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
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

    // --- miuix (LiquidGlass 悬浮底栏，照抄 wekit 0.9.4-rc01) ---
    implementation(libs.miuix.blur)
    implementation(libs.miuix.shader)
    implementation(libs.miuix.nav)
    // --- MaterialSymbols 图标（wekit 同款，悬浮底栏 tab 图标） ---
    implementation(libs.composablehorizons.material.symbols.outlined)
    implementation(libs.composablehorizons.material.symbols.filled)
}