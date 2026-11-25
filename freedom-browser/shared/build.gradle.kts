import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // Consumed by the Android app (JVM bytecode, Java 17 level).
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // Consumed by the Windows UI, which runs inside CEF (Chromium) as an ES module.
    js {
        outputModuleName.set("freedom-shared")
        browser {
            testTask { enabled = false } // shared logic is tested on the JVM; JS facade is smoke-tested by scripts/verify-shared-js.mjs
        }
        useEsModules()
        binaries.library()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

// Tests read shared conformance vectors relative to the module directory.
tasks.withType<Test>().configureEach { workingDir = projectDir }
