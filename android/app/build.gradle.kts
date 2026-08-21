plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val generatedIconResDir = layout.buildDirectory.dir("generated/launcherIcon/res").get().asFile
val generateLauncherIcon by tasks.registering {
    val sourceIcon = rootProject.file("../MAGCalc/Assets.xcassets/AppIcon.appiconset/AppIcon.png")
    inputs.file(sourceIcon)
    outputs.dir(generatedIconResDir)
    doLast {
        if (!sourceIcon.isFile) throw GradleException("Canonical MAGCalc AppIcon is missing: ${sourceIcon.path}")
        val source = javax.imageio.ImageIO.read(sourceIcon)
            ?: throw GradleException("Canonical MAGCalc AppIcon could not be decoded")
        listOf(
            generatedIconResDir.resolve("drawable-nodpi/app_icon_source.png"),
            generatedIconResDir.resolve("mipmap-nodpi/ic_launcher.png"),
        ).forEach { output ->
            output.parentFile.mkdirs()
            if (!javax.imageio.ImageIO.write(source, "png", output)) {
                throw GradleException("Could not encode normalized MAGCalc launcher icon")
            }
        }
    }
}

android {
    namespace = "de.kamilunavo.magcalc"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.kamilunavo.magcalc"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets.getByName("main").res.srcDir(generatedIconResDir)
    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateLauncherIcon)
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")

    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("com.android.billingclient:billing-ktx:9.1.0")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
