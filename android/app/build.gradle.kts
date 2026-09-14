import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.outlook.victoreduardo.aplicacionmovilbasica"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.outlook.victoreduardo.aplicacionmovilbasica"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// El backend siempre se expone en localhost:5000 (ver docker-compose.yml).
// Al instalar la app en un dispositivo/emulador, "adb reverse" hace que
// 127.0.0.1:5000 dentro de este apunte al 127.0.0.1:5000 del equipo host,
// sin importar la IP de red de cada máquina. Se aplica a TODOS los
// dispositivos/emuladores conectados (adb a secas falla si hay más de uno).
abstract class AdbReverseTask : DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    @get:org.gradle.api.tasks.Optional
    abstract val localPropertiesFile: RegularFileProperty

    @TaskAction
    fun run() {
        val sdkDir = System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: run {
                val props = Properties()
                val file = localPropertiesFile.orNull?.asFile
                if (file != null && file.exists()) {
                    file.inputStream().use { props.load(it) }
                }
                props.getProperty("sdk.dir")
            }
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val adbFile = sdkDir?.let { File(it, "platform-tools/${if (isWindows) "adb.exe" else "adb"}") }
        val adbPath = adbFile?.takeIf { it.exists() }?.absolutePath ?: "adb"

        try {
            val devicesOutput = ProcessBuilder(adbPath, "devices")
                .redirectErrorStream(true)
                .start()
                .let { it.waitFor(); it.inputStream.bufferedReader().readText() }
            val serials = devicesOutput.lines()
                .drop(1)
                .mapNotNull { line -> line.split("\t").firstOrNull()?.trim()?.takeIf { it.isNotEmpty() && line.contains("\tdevice") } }

            for (serial in serials) {
                ProcessBuilder(adbPath, "-s", serial, "reverse", "tcp:5000", "tcp:5000")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor()
            }
        } catch (e: Exception) {
            // No hay adb/SDK disponible (p.ej. compilando fuera de Android Studio); ignorar.
        }
    }
}

tasks.register<AdbReverseTask>("adbReverse") {
    localPropertiesFile.set(rootProject.layout.projectDirectory.file("local.properties"))
}

// Android Studio no siempre corre la tarea Gradle "installDebug" al pulsar Run
// (a veces instala el APK directamente con su propio deployer), así que enganchamos
// "adbReverse" a "preDebugBuild": esa tarea SIEMPRE se ejecuta al compilar la
// variante debug, sea cual sea el mecanismo que use el IDE para instalarla después.
tasks.whenTaskAdded {
    if (name == "preDebugBuild") {
        dependsOn("adbReverse")
    }
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
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Retrofit & Gson para conectarse a Flask
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // DataStore para guardar el Token JWT
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.compose.material:material-icons-extended")
}