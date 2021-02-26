import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform") version "1.4.20"
}

val GROUP: String by project
val VERSION_NAME: String by project

group = GROUP
version = VERSION_NAME

val isIdeaActive = System.getProperty("idea.active") == "true"

fun configInterop(target: KotlinNativeTarget) {
    val main by target.compilations.getting
    val sqlite3 by main.cinterops.creating {
        includeDirs("$projectDir/src/include")
    }

    target.compilations.forEach { kotlinNativeCompilation ->
        kotlinNativeCompilation.kotlinOptions.freeCompilerArgs += when {
            HostManager.hostIsLinux -> listOf(
                "-linker-options",
                "-lsqlite3 -L/usr/lib/x86_64-linux-gnu" //just /usr/lib for arch
            )
            HostManager.hostIsMingw -> listOf("-linker-options", "-Lc:\\msys64\\mingw64\\lib")
            else -> listOf("-linker-options", "-lsqlite3")
        }
    }
}

fun KotlinMultiplatformExtension.registerTargets(): List<KotlinNativeTarget> {
    return if (isIdeaActive) {
        listOf(
            macosX64("nativeCommon"),
            mingwX64("mingw"),
            linuxX64()
        )
    } else {
        //prevent some cross compilation to speed up builds on CI
        when {
            HostManager.hostIsLinux -> listOf(linuxX64())
            HostManager.hostIsMingw -> listOf(mingwX64("mingw"))
            else -> listOf(
                macosX64(),
                iosX64(),
                iosArm64(),
                iosArm32(),
                watchosArm32(),
                watchosArm64(),
                watchosX86(),
                tvosArm64(),
                tvosX64()
            )
        }
    }
}

kotlin {
    val knTargets = registerTargets()

    knTargets
        .forEach { target ->
            configInterop(target)
        }

    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib-common")
            }
        }
        commonTest {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test-common")
                implementation("org.jetbrains.kotlin:kotlin-test-annotations-common")
            }
        }

        val nativeCommonMain = sourceSets.maybeCreate("nativeCommonMain")
        val nativeCommonTest = sourceSets.maybeCreate("nativeCommonTest")

        val appleMain = sourceSets.maybeCreate("appleMain").apply {
            dependsOn(nativeCommonMain)
        }
        val linuxMain = sourceSets.maybeCreate("linuxX64Main").apply {
            dependsOn(nativeCommonMain)
        }

        if (!isIdeaActive) {
            val mingwMain = sourceSets.maybeCreate("mingwMain").apply {
                dependsOn(nativeCommonMain)
            }
            knTargets.forEach { target ->
                when {
                    target.name.startsWith("mingw") -> {
                        target.compilations.getByName("main").source(mingwMain)
                        target.compilations.getByName("test").source(nativeCommonTest)
                    }
                    target.name.startsWith("linux") -> {
                        target.compilations.getByName("main").source(linuxMain)
                        target.compilations.getByName("test").source(nativeCommonTest)
                    }
                    else -> {
                        target.compilations.getByName("main").source(appleMain)
                        target.compilations.getByName("test").source(nativeCommonTest)
                    }
                }
            }
        }
    }
}

apply(from = "../gradle/gradle-mvn-mpp-push.gradle")