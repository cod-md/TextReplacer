plugins {
    id("com.android.application") version "9.1.0" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
}
