plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

sourceSets {
    main {
        kotlin.srcDir("src/jvmMain/kotlin")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.xerial:sqlite-jdbc:3.53.1.0")
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
}

compose.desktop {
    application {
        mainClass = "com.uwapo.desktop.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "UWAPO"
            packageVersion = "1.0.0"
            
            description = "UWAPO Financial Management Desktop Application"
            copyright = "© 2025 UWAPO. All rights reserved."
            vendor = "UWAPO"
        }
    }
}
