plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Pure Kotlin/JVM: vector maths, centring, kNN, UMAP-lite, clustering, naming, camera maths. No Android classes.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// The tests read the real demo vectors that ship in the app assets.
sourceSets {
    test {
        resources.srcDir("../app/src/main/assets/demo")
    }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation("junit:junit:4.13.2")
}

// Rebuilds the demo layout, clusters and names from the shipped vectors (see tools/DemoGenerator.kt).
tasks.register<JavaExec>("generateDemo") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("app.embeddingspace.explorer.domain.tools.DemoGeneratorKt")
    args(file("../app/src/main/assets/demo").absolutePath)
}
