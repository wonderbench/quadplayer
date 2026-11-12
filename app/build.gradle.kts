import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinKapt)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.hilt)
}

val releaseStore = providers.environmentVariable("QP_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("QP_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("QP_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("QP_RELEASE_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(releaseStore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
require(releaseSigningValues.all { it.isNullOrBlank() } || releaseSigningValues.none { it.isNullOrBlank() }) {
    "Set all four QP_RELEASE_* signing variables, or none for an unsigned release."
}
val suppliedVersionCode = providers.environmentVariable("QP_VERSION_CODE").orNull
val releaseVersionCode = suppliedVersionCode?.toIntOrNull()
require(suppliedVersionCode == null || (releaseVersionCode != null && releaseVersionCode > 0)) {
    "QP_VERSION_CODE must be a positive integer."
}

android {
    namespace = "com.quadplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.quadplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode ?: 1
        versionName = providers.environmentVariable("QP_VERSION_NAME").orNull ?: "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (!releaseStore.isNullOrBlank()) create("externalRelease") {
            storeFile = file(releaseStore)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("externalRelease")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        baseline = file("lint-baseline.xml")
        warningsAsErrors = true
        abortOnError = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

kapt {
    correctErrorTypes = true
    arguments { arg("room.schemaLocation", "$projectDir/schemas") }
}

dependencies {
    val composeBom = platform(libs.composeBom)

    implementation(composeBom)
    implementation(libs.activityCompose)
    implementation(libs.coreKtx)
    implementation(libs.splashscreen)
    implementation(libs.lifecycleCompose)
    implementation(libs.lifecycleKtx)
    implementation(libs.lifecycleViewmodelCompose)
    implementation(libs.composeUi)
    implementation(libs.composeGraphics)
    implementation(libs.composePreview)
    implementation(libs.composeFoundation)
    implementation(libs.composeMaterial3)
    implementation(libs.composeIcons)
    implementation(libs.material)
    implementation(libs.navigationCompose)
    implementation(libs.hiltNavigationCompose)
    implementation(libs.hiltAndroid)
    kapt(libs.hiltCompiler)
    implementation(libs.roomRuntime)
    implementation(libs.roomKtx)
    kapt(libs.roomCompiler)
    implementation(libs.datastorePreferences)
    implementation(libs.coroutinesAndroid)
    implementation(libs.media3Exoplayer)
    implementation(libs.media3Ui)
    implementation(libs.media3Session)
    // Preserve versions previously supplied by Coil for Media3 and DataStore.
    implementation(libs.exifinterface)
    implementation(libs.okioJvm)

    testImplementation(libs.junit)
    testImplementation(libs.coroutinesTest)

    debugImplementation(libs.composeTooling)
    androidTestImplementation(libs.androidxTestJunit)
    androidTestImplementation(libs.androidxTestRunner)
    androidTestImplementation(libs.androidxTestCore)
    androidTestImplementation(libs.espressoCore)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.composeTestJunit)
    debugImplementation(libs.composeTestManifest)
}
