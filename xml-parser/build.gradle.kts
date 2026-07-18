plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        java.srcDirs("/Users/awiner/Developer/scythebill-git/birdlist/xml-parser/src/main/java")
    }
    test {
        java.srcDirs("/Users/awiner/Developer/scythebill-git/birdlist/xml-parser/src/test/java")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}
