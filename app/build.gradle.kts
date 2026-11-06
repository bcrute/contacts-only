import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.application")
}

android {
    namespace = "com.ben.contactsonly"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.contactsonly.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"
        testInstrumentationRunner = "com.ben.contactsonly.AndroidIntegration"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("org.mockito:mockito-core:5.14.2")
}

// Robolectric fetches SDK runtime jars independently of Gradle's repositories.
// Let the fleet route those requests through its existing Maven mirror too.
tasks.withType<Test>().configureEach {
    providers.environmentVariable("ROBOLECTRIC_DEPENDENCY_REPO_URL").orNull?.let {
        systemProperty("robolectric.dependency.repo.url", it)
    }
}
