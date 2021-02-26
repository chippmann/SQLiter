plugins {
	kotlin("multiplatform") version "1.4.20"
}

val GROUP:String by project
val VERSION_NAME:String by project

group = GROUP
version = VERSION_NAME

val ideaActive = System.getProperty("idea.active") == "true"

fun configInterop(target: org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget) {
	val main by target.compilations.getting
	val sqlite3 by main.cinterops.creating {
		includeDirs("$projectDir/src/include")
	}
}

val onWindows = org.jetbrains.kotlin.konan.target.HostManager.hostIsMingw

kotlin {
	val knTargets = if (ideaActive) {
		listOf(
			macosX64("nativeCommon"),
			mingwX64("mingw"),
			linuxX64()
		)
	} else {
		listOf(
			macosX64(),
			iosX64(),
			iosArm64(),
			iosArm32(),
			watchosArm32(),
			watchosArm64(),
			watchosX86(),
			tvosArm64(),
			tvosX64(),
			mingwX64("mingw") {
				compilations.forEach {
					it.kotlinOptions.freeCompilerArgs += listOf("-linker-options", "-Lc:\\msys64\\mingw64\\lib")
				}
			},
			linuxX64()
		)
	}

	knTargets.forEach { target ->
		configInterop(target)
		val test by target.compilations.getting

		if (target.name.startsWith("linux")) {
			test.kotlinOptions.freeCompilerArgs += listOf(
				"-linker-options",
				"-lsqlite3 -L/usr/lib/x86_64-linux-gnu"
			) //just /usr/lib for arch
		} else {
			test.kotlinOptions.freeCompilerArgs += listOf("-linker-options", "-lsqlite3")
		}
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

		if (!ideaActive) {
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