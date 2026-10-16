plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("maven-publish")
    id("signing")
    id("com.vanniktech.maven.publish") version "0.32.0"
    id("com.diffplug.spotless") version "8.0.0"
}

group = "cloud.eppo"
version = "1.0.0-SNAPSHOT"

android {
    namespace = "cloud.eppo.kotlin"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "META-INF/**"
        }
    }
}

dependencies {
    // Shared types from sdk-common-jvm
    api("cloud.eppo:sdk-common-jvm:3.13.1") {
        // Exclude Jackson since we use kotlinx.serialization
        exclude(group = "com.fasterxml.jackson.core")
    }

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // JSON Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.17")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.google.truth:truth:1.1.5")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint()
    }
}

signing {
    if (System.getenv("GPG_PRIVATE_KEY") != null && System.getenv("GPG_PASSPHRASE") != null) {
        useInMemoryPgpKeys(System.getenv("GPG_PRIVATE_KEY"), System.getenv("GPG_PASSPHRASE"))
    }
    sign(publishing.publications)
}

tasks.withType<Sign> {
    onlyIf {
        (System.getenv("GPG_PRIVATE_KEY") != null && System.getenv("GPG_PASSPHRASE") != null) ||
                (project.hasProperty("signing.keyId") &&
                        project.hasProperty("signing.password") &&
                        project.hasProperty("signing.secretKeyRingFile"))
    }
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates("cloud.eppo", "eppo-kotlin", project.version as String)

    pom {
        name.set("Eppo Kotlin SDK")
        description.set("Eppo Kotlin SDK for precomputed feature flags on Android")
        url.set("https://github.com/Eppo-exp/android-sdk")
        licenses {
            license {
                name.set("MIT License")
                url.set("http://www.opensource.org/licenses/mit-license.php")
            }
        }
        developers {
            developer {
                name.set("Eppo")
                email.set("https://www.geteppo.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/Eppo-exp/android-sdk.git")
            developerConnection.set("scm:git:ssh://github.com/Eppo-exp/android-sdk.git")
            url.set("https://github.com/Eppo-exp/android-sdk/tree/main")
        }
    }
}
