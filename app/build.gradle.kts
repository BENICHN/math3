import java.nio.file.Paths

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.benichn.math3"
    compileSdkVersion(33)

    defaultConfig {
        applicationId = "fr.benichn.math3"
        minSdkVersion(26)
        targetSdkVersion(33)
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        compileSdkPreview = "UpsideDownCake"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    packagingOptions {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            pickFirsts += listOf("**/*.xsd", "META-INF/*.md")
        }
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    configurations.all {
        resolutionStrategy.capabilitiesResolution {
            withCapability("com.google.collections:google-collections") {
                select("com.google.guava:guava:0")
            }
            // and/or
            withCapability("com.google.guava:listenablefuture") {
                select("com.google.guava:guava:0")
            }
        }
    }

    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.6.0")
    implementation("androidx.navigation:navigation-ui-ktx:2.6.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    // implementation("com.hierynomus:sshj:0.35.0")
    // implementation("org.bouncycastle:bcpkix-jdk15on:1.70")
    implementation("org.zeromq:jeromq:0.5.3")
    implementation("com.google.guava:guava:32.1.2-jre")
    implementation("org.matheclipse:matheclipse-gpl:3.0.0-SNAPSHOT") {
        exclude(group = "com.google.guava", module = "guava")
    }
    implementation("org.matheclipse:matheclipse-core:3.0.0-SNAPSHOT") {
        exclude(group = "com.google.guava", module = "guava")
    }
    // https://mvnrepository.com/artifact/com.google.code.gson/gson
    implementation("com.google.code.gson:gson:2.10.1")
}

// tasks.register("generateFunction") {
//     doLast {
//         val outputDir = File("${project.rootDir}/app/src/main/java/fr/benichn/math3/graphics/boxes") // Répertoire de sortie de la fonction générée
//         outputDir.mkdirs()
//
//         val functionName = "maFonction"
//         val functionCode = """
//             package fr.benichn.math3.graphics.boxes
//
//             object FormulaBoxSerializer {
//                 fun g() {
//
//                 }
//             }
//         """.trimIndent()
//
//         File(outputDir, "FormulaBoxSerializer.kt").writeText(functionCode)
//     }
// }