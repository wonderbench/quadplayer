import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.CommandLineArgumentProvider
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.hilt)
}

abstract class VerifyThirdPartyNotices : DefaultTask() {
    @get:Input abstract val components: ListProperty<String>
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val recordedComponents: RegularFileProperty

    @TaskAction fun verify() {
        val actual = components.get().toSet()
        val recorded = recordedComponents.get().asFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        check(actual == recorded) {
            "Release dependencies changed; review licenses and update release_components.txt. " +
                "Missing: ${actual - recorded}; no longer resolved: ${recorded - actual}"
        }
    }
}

class RoomSchemaArgProvider(
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    val schemaDir: File,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> = listOf("room.schemaLocation=${schemaDir.path}")
}

abstract class AboutDocumentation : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE) abstract val structure: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE) abstract val strings: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE) abstract val document: RegularFileProperty
    @get:Input abstract val update: Property<Boolean>

    @TaskAction fun generate() {
        fun xml(file: java.io.File) = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isXIncludeAware = false
            setExpandEntityReferences(false)
        }.newDocumentBuilder().parse(file)
        fun elements(nodes: org.w3c.dom.NodeList): List<Element> = (0 until nodes.length).map { nodes.item(it) as Element }
        val names = elements(xml(strings.get().asFile).getElementsByTagName("string"))
            .associate { it.getAttribute("name") to it.textContent.trim() }
        val structureDoc = xml(structure.get().asFile)
        val arrays = elements(structureDoc.getElementsByTagName("string-array"))
            .associate { array -> array.getAttribute("name") to elements(array.getElementsByTagName("item")).map { item ->
                val raw = item.textContent.trim()
                require(raw.startsWith("@string/")) { "Invalid About string reference: $raw" }
                val ref = raw.removePrefix("@string/")
                require(names.containsKey(ref)) { "Unknown About string: $ref" }
                names.getValue(ref).also { value ->
                    require('\\' !in value && '\n' !in value && !Regex("%[0-9]+\\$").containsMatchIn(value)) {
                        "About string $ref uses formatting that the documentation generator cannot render safely"
                    }
                }
            } }
        fun values(name: String): List<String> = arrays[name] ?: error("Missing About array: $name")
        val groups = elements(structureDoc.getElementsByTagName("array")).associate { array ->
            array.getAttribute("name") to elements(array.getElementsByTagName("item")).map { item ->
                val ref = item.textContent.trim()
                require(ref.startsWith("@array/")) { "Invalid About group: $ref" }
                ref.removePrefix("@array/").also { require(arrays.containsKey(it)) { "Unknown About group: $it" } }
            }
        }
        fun groupNames(name: String) = groups[name] ?: error("Missing About group list: $name")
        fun escape(value: String) = value.replace("|", "\\|")
        val headings = values("about_headings").also { require(it.size == 4) }
        val requirements = values("about_requirements").also { require(it.size >= 3 && it.size % 2 == 1) }
        val generated = buildString {
            appendLine("# QuadPlayer — 多视频同步播放器")
            appendLine(); appendLine("## ${headings[0]}"); appendLine(); appendLine(values("about_intro").single())
            appendLine(); appendLine("## ${headings[1]}")
            groupNames("about_feature_groups").forEach { key ->
                val group = values(key); require(group.size >= 2)
                appendLine(); appendLine("### ${group.first()}"); appendLine()
                group.drop(1).forEach { appendLine("- $it") }
            }
            appendLine(); appendLine("## ${headings[2]}"); appendLine()
            appendLine("| 项目 | 要求 |"); appendLine("| --- | --- |")
            requirements.dropLast(1).chunked(2).forEach { appendLine("| ${escape(it[0])} | ${escape(it[1])} |") }
            appendLine(); appendLine("> ${requirements.last()}")
            appendLine(); appendLine("## ${headings[3]}")
            groupNames("about_guide_groups").forEach { key ->
                val group = values(key); require(group.size >= 2)
                appendLine(); appendLine("### ${group.first()}"); appendLine()
                group.drop(1).forEachIndexed { index, item -> appendLine("${index + 1}. $item") }
            }
        }.trimEnd()
        val file = document.get().asFile
        val original = file.readText().replace("\r\n", "\n")
        val start = "<!-- BEGIN SHARED ABOUT -->"
        val end = "<!-- END SHARED ABOUT -->"
        require(original.indexOf(start) >= 0 && original.indexOf(start) == original.lastIndexOf(start))
        require(original.indexOf(end) > original.indexOf(start) && original.indexOf(end) == original.lastIndexOf(end))
        val expected = original.substringBefore(start) + start + "\n" + generated + "\n" + end + original.substringAfter(end)
        if (update.get()) file.writeText(expected) else check(original == expected) {
            "About.md is out of date. Run :app:syncAboutDocs and review the result."
        }
    }
}

fun AboutDocumentation.configureAbout(updateFile: Boolean) {
    structure.set(layout.projectDirectory.file("src/main/res/values/about_structure.xml"))
    strings.set(layout.projectDirectory.file("src/main/res/values/strings.xml"))
    document.set(layout.projectDirectory.file("../About.md"))
    update.set(updateFile)
}

tasks.register<AboutDocumentation>("verifyAboutDocs") { configureAbout(false) }
tasks.register<AboutDocumentation>("syncAboutDocs") { configureAbout(true) }

tasks.register<VerifyThirdPartyNotices>("verifyThirdPartyNotices") {
    recordedComponents.set(layout.projectDirectory.file("src/main/assets/licenses/release_components.txt"))
    components.set(configurations.getByName("releaseRuntimeClasspath").incoming.artifacts.resolvedArtifacts.map { artifacts ->
        artifacts.map { artifact ->
            val id = artifact.id.componentIdentifier as? ModuleComponentIdentifier
                ?: error("Unexpected non-module release dependency: ${artifact.id}")
            "${id.group}:${id.module}:${id.version}"
        }.distinct().sorted()
    })
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
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
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
        // Dependabot tracks dependency upgrades; online version suggestions vary by runner and date.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion")
        textReport = true
        textOutput = file("build/reports/lint-results-debug.txt")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg(RoomSchemaArgProvider(file("schemas")))
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
    ksp(libs.hiltCompiler)
    implementation(libs.roomRuntime)
    implementation(libs.roomKtx)
    ksp(libs.roomCompiler)
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
    androidTestImplementation(libs.roomTesting)
    androidTestImplementation(libs.androidxTestRunner)
    androidTestImplementation(libs.androidxTestCore)
    androidTestImplementation(libs.espressoCore)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.composeTestJunit)
    debugImplementation(libs.composeTestManifest)
}
