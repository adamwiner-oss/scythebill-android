plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

val birdlistRepo = "/Users/awiner/Developer/scythebill-git/birdlist"

kotlin {
    jvmToolchain(17)

    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            kotlin.srcDir("$birdlistRepo/xml-parser/src/commonMain/kotlin")
        }
        commonTest {
            kotlin.srcDir("$birdlistRepo/xml-parser/src/commonTest/kotlin")
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmMain {
            kotlin.srcDir("$birdlistRepo/xml-parser/src/jvmMain/kotlin")
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        iosMain {
            kotlin.srcDir("$birdlistRepo/xml-parser/src/iosMain/kotlin")
        }
    }
}
