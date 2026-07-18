plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        java {
            srcDirs("/Users/awiner/Developer/scythebill-git/birdlist/model/src/main/java")
            exclude("com/scythebill/birdlist/model/mapdata/**")
            exclude("com/scythebill/birdlist/model/sighting/upgrades/**")
        }
        resources {
            srcDirs("/Users/awiner/Developer/scythebill-git/birdlist/model/src/main/resources")
            exclude("ioc-taxon.xml")
        }
        // Checklists.enumerateChecklistFiles() locates checklist CSVs by walking
        // relative to its own class file's code-source directory (a Maven-era
        // assumption that classes and resources share one output dir). Gradle
        // splits them by default, so merge resources into the Java classes dir.
        output.setResourcesDir(layout.buildDirectory.dir("classes/java/main"))
    }
    test {
        java {
            srcDirs("/Users/awiner/Developer/scythebill-git/birdlist/model/src/test/java")
            exclude("com/scythebill/birdlist/model/mapdata/**")
            exclude("com/scythebill/birdlist/model/sighting/upgrades/**")
        }
        resources {
            srcDirs("/Users/awiner/Developer/scythebill-git/birdlist/model/src/test/resources")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":xml-parser"))

    implementation("com.google.guava:guava:33.5.0-jre")
    implementation("org.apache.commons:commons-lang3:3.20.0")
    implementation("commons-io:commons-io:2.22.0")
    implementation("joda-time:joda-time:2.14.2")
    implementation("com.opencsv:opencsv:5.12.0")
    implementation("com.google.code.findbugs:jsr305:3.0.2")

    // Guice's @Inject annotation only, for compile-time constructor markers.
    // Not on the Android runtime classpath — no DI framework/Guice on Android.
    compileOnly("com.google.inject:guice:6.0.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.5")
}

tasks.test {
    useJUnit()
}
