import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinSerialization)
}

group = "io.github.snd_r.komelia.db.sqlite"
version = "unspecified"

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    android {
        namespace = "io.github.snd_r.komelia.infra.database.sqlite"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
        androidResources { enable = true }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
        }
        commonMain.dependencies {
            implementation(projects.komeliaDomain.core)
            implementation(projects.komeliaDomain.offline)
            implementation(projects.komeliaDomain.komgaApi)
            implementation(projects.komeliaInfra.database.shared)
            implementation(projects.komeliaInfra.imageDecoder.shared)

            implementation(libs.compose.runtime)
            implementation(libs.compose.resources)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.filekit.core)
            implementation(libs.exposed.core)
            implementation(libs.exposed.jdbc)
            implementation(libs.exposed.json)
            implementation(libs.exposed.kotlin.datetime)
            implementation(libs.hikariCP)
            implementation(libs.flyway.core)
            implementation(libs.sqlite.xerial.jdbc)
        }
    }
}

val sqliteExtract: Configuration by configurations.creating
dependencies { sqliteExtract(libs.sqlite.xerial.jdbc) }

// Android cannot load the .so directly from the JDBC jar. Generate a JNI source
// directory and wire its producer into every Android build, including assembleDebug.
abstract class ExtractAndroidSqlite : Sync() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty
}

val extractAndroidSqlite = tasks.register<ExtractAndroidSqlite>("extractAndroidSqlite") {
    outputDirectory.set(layout.buildDirectory.dir("generated/androidSqliteJni"))
    from(provider { zipTree(sqliteExtract.singleFile) }) {
        include("org/sqlite/native/Linux-Android/*/libsqlitejdbc.so")
        eachFile {
            val abi = when (relativePath.segments[4]) {
                "aarch64" -> "arm64-v8a"
                "arm" -> "armeabi-v7a"
                "x86_64" -> "x86_64"
                "x86" -> "x86"
                else -> error("Unsupported SQLite Android architecture: $path")
            }
            relativePath = RelativePath(true, abi, name)
        }
        includeEmptyDirs = false
    }
    into(outputDirectory)
}

extensions.configure<KotlinMultiplatformAndroidComponentsExtension> {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(extractAndroidSqlite) { it.outputDirectory }
    }
}
// Keep the documented native-build entry points, without copying duplicate .so
// files into src/androidMain alongside the generated JNI directory.
listOf("arm64", "armv7a", "x86_64", "x86").forEach { arch ->
    tasks.register("android-$arch-ExtractSqliteLib") { dependsOn(extractAndroidSqlite) }
}
