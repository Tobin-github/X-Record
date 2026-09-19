import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * 签名配置。
 *
 * 口令不写在本文件里——本文件会进版本库，而 jks 与口令一旦泄露，
 * 任何人都能签出被系统当成"正常更新"的安装包。签名信息放在
 * 根目录的 keystore.properties（已 gitignore），本文件只负责读取。
 *
 * 找不到该文件时（例如新克隆的仓库）不配置签名：
 * debug 回落到默认调试密钥，release 保持未签名，保证依然能构建。
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val releaseKeystoreFile = keystoreProperties.getProperty("storeFile")
    ?.let { rootProject.file(it) }
val hasReleaseKeystore = keystorePropertiesFile.exists() &&
    releaseKeystoreFile != null &&
    releaseKeystoreFile.exists()

android {
    namespace = "top.tobin.xrecord"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "top.tobin.xrecord"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("xrecord") {
                storeFile = releaseKeystoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 与 release 使用同一套密钥：两个变体签名一致，
            // 互相覆盖安装时不会因为签名不同而失败
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("xrecord")
            }
        }
        release {
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("xrecord")
            }
            optimization {
                // 开启 R8：压缩 + 混淆 + 字节码优化。
                // 关闭时 src/main/keepRules 下的规则不会生效。
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // 关于页需要读取版本号
        buildConfig = true
    }
}

// Room 导出 schema 到版本库，任何表结构变更都必须配套迁移与迁移测试
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.vico.compose.m3)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
