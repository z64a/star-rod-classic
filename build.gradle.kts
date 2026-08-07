import org.gradle.api.tasks.bundling.Zip
import java.util.Locale
import java.util.Properties

val appProperties = Properties().apply {
    file("app.properties").inputStream().use { load(it) }
}

val appMain = "app.StarRodClassic"
val appVersion = requireNotNull(appProperties.getProperty("version")) {
    "app.properties must define a version"
}
val targetJavaVersion = 17

repositories {
    mavenCentral()
}

plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.11"
    id("net.nemerosa.versioning") version "3.1.0"
    id("com.cmgapps.licenses") version "4.8.0"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

val manualGenerator by sourceSets.creating {
    java.srcDir("src/manual/java")
}

tasks.compileJava {
    options.release.set(targetJavaVersion)
    options.compilerArgs.add("-Xlint:deprecation")
}

val operatingSystem = System.getProperty("os.name")
val osName = when {
    operatingSystem.startsWith("Windows", ignoreCase = true) -> "windows"
    operatingSystem.startsWith("Mac", ignoreCase = true) ||
        operatingSystem.startsWith("Darwin", ignoreCase = true) -> "macos"
    operatingSystem.startsWith("Linux", ignoreCase = true) -> "linux"
    else -> throw GradleException("Releases are not supported on $operatingSystem")
}
val isWindows = osName == "windows"

val architecture = System.getProperty("os.arch").lowercase(Locale.ROOT)
val archName = when (architecture) {
    "x86_64", "amd64" -> "x64"
    "aarch64", "arm64" -> "arm64"
    else -> throw GradleException("Releases are not supported on $architecture")
}

val lwjglNatives = "natives-$osName${if (archName == "arm64") "-arm64" else ""}"
val platformName = "$osName-$archName"

dependencies {
    implementation(platform("org.lwjgl:lwjgl-bom:3.3.3"))

    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-opengl")
    implementation("org.lwjgl:lwjgl-jawt")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-tinyfd")
    implementation("org.lwjgl:lwjgl-assimp")

    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-tinyfd::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-assimp::$lwjglNatives")

    implementation("org.lwjglx:lwjgl3-awt:0.1.8")

    implementation("commons-io:commons-io:2.16.1")
    implementation("org.apache.commons:commons-text:1.12.0")
    implementation("org.apache.commons:commons-lang3:3.14.0")

    implementation("com.miglayout:miglayout-core:11.3")
    implementation("com.miglayout:miglayout-swing:11.3")

    implementation("com.alexandriasoftware.swing:jsplitbutton:1.3.1")
    implementation("com.alexdupre:pngj:2.1.2.1")

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.yaml:snakeyaml:2.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")

    implementation("com.formdev:flatlaf:3.4.1")
    implementation("com.formdev:flatlaf-intellij-themes:3.4.1")
    implementation("com.formdev:flatlaf-extras:3.4.1")

    implementation(files("lib/org.eclipse.cdt.core-5.11.0.jar"))
    implementation(files("lib/org.eclipse.equinox.common-3.6.0.jar"))

    implementation("org.ahocorasick:ahocorasick:0.6.3")

    add(manualGenerator.implementationConfigurationName, "org.commonmark:commonmark:0.29.0")
    add(manualGenerator.implementationConfigurationName, "org.commonmark:commonmark-ext-gfm-tables:0.29.0")
    add(manualGenerator.implementationConfigurationName, "org.commonmark:commonmark-ext-heading-anchor:0.29.0")
}

tasks.test {
    useJUnitPlatform()
}

val licenseBuildDir = layout.buildDirectory.dir("reports/licenses/licenseReport")
val manualBuildDir = layout.buildDirectory.dir("generated/manual")
val releaseBuildDir = layout.buildDirectory.dir("release")
val runtimeBuildDir = layout.buildDirectory.dir("runtime/$platformName")
val javaCompiler = javaToolchains.compilerFor(java.toolchain)

val renderManual by tasks.registering(JavaExec::class) {
    dependsOn(manualGenerator.classesTaskName)

    group = "documentation"
    description = "Render the user guide as HTML"
    classpath = manualGenerator.runtimeClasspath
    mainClass.set("manual.ManualGenerator")
    args(file("manual").absolutePath, manualBuildDir.get().asFile.absolutePath)

    inputs.dir(file("manual"))
    outputs.dir(manualBuildDir)

    doFirst {
        delete(manualBuildDir)
    }
}

tasks.compileJava {
    dependsOn(renderManual)
}

tasks.shadowJar {
    mergeServiceFiles()
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("META-INF/LICENSE")
    archiveFileName.set("StarRod.jar")

    manifest {
        attributes["Main-Class"] = appMain
        attributes["App-Version"] = appVersion
        attributes["Build-Branch"] = versioning.info.branchId
        attributes["Build-Commit"] = versioning.info.commit
        if (versioning.info.tag != null)
            attributes["Build-Tag"] = versioning.info.tag
    }
}

val cleanRuntime by tasks.registering(Delete::class) {
    delete(runtimeBuildDir)
}

val createRuntime by tasks.registering(Exec::class) {
    dependsOn(cleanRuntime)

    val jlink = javaCompiler.map {
        it.metadata.installationPath.file("bin/${if (isWindows) "jlink.exe" else "jlink"}")
    }

    args(
        "--add-modules",
        // java.desktop brings in AWT/Swing, image I/O, sound, XML and prefs.
        // The other modules cover logging, reflective library access and TLS.
        "java.desktop,java.logging,java.management,java.naming,jdk.crypto.ec,jdk.unsupported",
        "--strip-debug",
        "--no-header-files",
        "--no-man-pages",
        "--compress=2",
        "--output",
        runtimeBuildDir.get().asFile.absolutePath
    )

    doFirst {
        executable(jlink.get().asFile)
    }
}

tasks.register<Zip>("createReleaseZip") {
    dependsOn(tasks.shadowJar, createRuntime, tasks.licenseReport, renderManual)

    group = "release"
    description = "Create a release for $platformName"

    from(tasks.shadowJar)
    from(runtimeBuildDir) {
        into("runtime")
    }
    from(file("database")) {
        into("database")
    }
    from(file("contributed")) {
        into("contributed")
    }
    from(manualBuildDir) {
        into("manual")
    }
    from(licenseBuildDir) {
        into("database/licenses")
    }
    from(file("exec")) {
        include(if (isWindows) "StarRod.bat" else "StarRod")
    }

    if (!isWindows) {
        eachFile {
            if (name == "StarRod" || path.startsWith("runtime/bin/"))
                permissions { unix("755") }
        }
    }

    val commitHash = versioning.info.build
    val releaseTag = versioning.info.tag?.takeIf { it.startsWith("v") }

    if (releaseTag != null) {
        require(releaseTag == "v$appVersion") {
            "Release tag $releaseTag does not match app version $appVersion"
        }
    }

    val releaseVersion = if (releaseTag != null) {
        appVersion
    } else {
        "$appVersion-$commitHash"
    }

    archiveFileName.set("StarRod-$releaseVersion-$platformName.zip")
    destinationDirectory.set(releaseBuildDir)
}
