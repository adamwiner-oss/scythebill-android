plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

val birdlistRepo = "/Users/awiner/Developer/scythebill-git/birdlist"

kotlin {
    jvmToolchain(17)

    jvm()

    sourceSets {
        commonMain {
            kotlin.srcDir("$birdlistRepo/model/src/commonMain/kotlin")
            dependencies {
                api(project(":xml-parser"))
                api("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
            }
        }
        jvmMain {
            kotlin.srcDir("$birdlistRepo/model/src/jvmMain/java")
            kotlin.exclude("com/scythebill/birdlist/model/mapdata/**")
            kotlin.exclude("com/scythebill/birdlist/model/sighting/upgrades/**")
            resources.srcDir("$birdlistRepo/model/src/main/resources")
            resources.exclude("ioc-taxon.xml")
            dependencies {
                implementation("com.google.guava:guava:33.5.0-jre")
                implementation("org.apache.commons:commons-lang3:3.20.0")
                implementation("commons-io:commons-io:2.22.0")
                implementation("joda-time:joda-time:2.14.2")
                implementation("com.opencsv:opencsv:5.12.0")
                implementation("com.google.code.findbugs:jsr305:3.0.2")

                // Guice's @Inject annotation only, for compile-time constructor markers.
                // Not on the Android runtime classpath — no DI framework/Guice on Android.
                compileOnly("com.google.inject:guice:6.0.0")
            }
        }
        jvmTest {
            kotlin.srcDir("$birdlistRepo/model/src/test/java")
            kotlin.exclude("com/scythebill/birdlist/model/mapdata/**")
            kotlin.exclude("com/scythebill/birdlist/model/sighting/upgrades/**")
            resources.srcDir("$birdlistRepo/model/src/test/resources")
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("com.google.truth:truth:1.4.5")
            }
        }
    }
}

// The jvm target's compileJvmMainJava/compileJvmTestJava tasks default to
// scanning src/jvmMain/java and src/jvmTest/java relative to the project
// directory, which don't exist here — the sources live in birdlistRepo, so
// point the tasks at the same external directories used above for `kotlin`.
tasks.named<JavaCompile>("compileJvmMainJava") {
    source = fileTree("$birdlistRepo/model/src/jvmMain/java") {
        exclude("com/scythebill/birdlist/model/mapdata/**")
        exclude("com/scythebill/birdlist/model/sighting/upgrades/**")
    }
}
tasks.named<JavaCompile>("compileJvmTestJava") {
    source = fileTree("$birdlistRepo/model/src/test/java") {
        exclude("com/scythebill/birdlist/model/mapdata/**")
        exclude("com/scythebill/birdlist/model/sighting/upgrades/**")
    }
}

// Checklists.enumerateChecklistFiles() locates checklist CSVs by walking
// relative to its own class file's code-source directory when running from
// loose class files (as opposed to a jar). Merge processed resources into
// the same directory as the compiled classes so that lookup keeps working,
// matching what the old kotlin.jvm build did via output.setResourcesDir().
tasks.named<Copy>("jvmProcessResources") {
    destinationDir = tasks.named<JavaCompile>("compileJvmMainJava").get().destinationDirectory.get().asFile
}
tasks.named<Copy>("jvmTestProcessResources") {
    destinationDir = tasks.named<JavaCompile>("compileJvmTestJava").get().destinationDirectory.get().asFile
}
