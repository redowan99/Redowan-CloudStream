import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        // Shitpack repo which contains our tools and dependencies
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        // Cloudstream gradle plugin which makes everything work and builds plugins
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.10")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) = extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        // when running through github workflow, GITHUB_REPOSITORY should contain current repository name
        // you can modify it to use other git hosting services, like gitlab
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/redowan99/Redowan-CloudStream")
    }

    android {
        defaultConfig {
            minSdk = 21
            compileSdkVersion(35)
            targetSdk = 35
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        tasks.withType<KotlinCompile>().configureEach {
            kotlinOptions {
                jvmTarget = "1.8" // Required
                // Disables some unnecessary features
                freeCompilerArgs = freeCompilerArgs + listOf(
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions"
                )
            }
        }
    }

    dependencies {
        val runtimeOnly by configurations
        val implementation by configurations

        val activateCloudstreamApi = true
        if(activateCloudstreamApi){
            val apkTasks = listOf("deployWithAdb", "build")
            val useApk = gradle.startParameter.taskNames.any { taskName ->
                apkTasks.any { apkTask ->
                    taskName.contains(apkTask, ignoreCase = true)
                }
            }
            // If the task is specifically to compile the app then use the stubs, otherwise us the library.
            if (useApk) {
                // Stubs for all Cloudstream classes
                runtimeOnly("com.lagradost:cloudstream3:pre-release")
            } else {
                // For running locally
                implementation("com.github.Blatzar:CloudstreamApi:0.1.6")
            }
        }
        else{
            // Stubs for all Cloudstream classes
            runtimeOnly("com.github.recloudstream.cloudstream:library:pre-release")
        }


        // these dependencies can include any of those which are added by the app,
        // but you dont need to include any of them if you dont need them
        // https://github.com/recloudstream/cloudstream/blob/master/app/build.gradle
        implementation(kotlin("stdlib")) // adds standard kotlin features, like listOf, mapOf etc
        implementation("com.github.recloudstream.cloudstream:library:pre-release")
        implementation("com.github.Blatzar:NiceHttp:0.4.11") // http library
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.0")
    }
}

task<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
