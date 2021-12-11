import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.6.0"
    application
    id ("com.github.johnrengelman.shadow") version "7.1.0"

}

repositories {
    mavenCentral()
}

dependencies {

    implementation(platform("org.jetbrains.kotlin:kotlin-bom:${project.properties["kotlinVersion"]}"))
    implementation(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:${project.properties["coroutinesVersion"]}"))
    implementation(platform("software.amazon.awssdk:bom:${project.properties["awsSdkVersion"]}"))
    implementation(platform("com.fasterxml.jackson:jackson-bom:${project.properties["jacksonVersion"]}"))
    implementation(platform("io.ktor:ktor-bom:${project.properties["ktorVersion"]}"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")

    implementation("io.ktor:ktor-client-java")

    implementation("org.slf4j:jcl-over-slf4j:1.7.32")
    implementation("ch.qos.logback:logback-classic:1.2.7")
    implementation("net.logstash.logback:logstash-logback-encoder:7.0.1")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("javax.xml.bind:jaxb-api:2.4.0-b180830.0359")

    implementation("org.apache.commons:commons-compress:1.21")

    implementation("software.amazon.awssdk:s3")
    runtimeOnly("software.amazon.awssdk:sts")
}

application {
    mainClass.set("com.henrycourse.AppKt")
}

java {
    withSourcesJar()
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "16"
        }
    }
    compileJava {
        java {
            targetCompatibility = JavaVersion.VERSION_16
        }
    }
    compileTestKotlin {
        kotlinOptions {
            jvmTarget = "16"
        }
    }
    compileTestJava {
        targetCompatibility = "16"
    }

    withType<Test> {
        useJUnitPlatform()
        systemProperties["kotest.framework.parallelism"] = 4
    }

    withType<ShadowJar> {
        archiveBaseName.set("app")
        manifest {
            attributes["Main-Class"] = "com.henrycourse.AppKt"
        }
    }
}

