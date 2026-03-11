buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.6.1")
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij.platform)
    alias(libs.plugins.detekt)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(providers.gradleProperty("javaVersion").get().toInt())
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(
            providers.gradleProperty("platformType"),
            providers.gradleProperty("platformVersion"),
        )
        bundledPlugin("org.jetbrains.plugins.terminal")
        instrumentationTools()
        pluginVerifier()
        zipSigner()
    }

    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
}

// Exclude IDE-provided libraries from plugin distribution
configurations {
    runtimeClasspath {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains", module = "annotations")
    }
}

intellijPlatform {
    pluginConfiguration {
        id = providers.gradleProperty("pluginGroup")
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }

        vendor {
            name = "Jakub Jirák"
            url = "https://github.com/jakubjirak"
        }
    }

    pluginVerification {
        ides {
            ide("IC", "2023.2.8")
            ide("IC", "2024.3")
        }
    }

    signing {
        // Configure via environment variables:
        // ORG_GRADLE_PROJECT_signCertificateChain
        // ORG_GRADLE_PROJECT_signPrivateKey
        // ORG_GRADLE_PROJECT_signPassword
    }

    publishing {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

tasks {
    publishPlugin {
        token.set(System.getenv("PLUGIN_TOKEN"))
    }

    test {
        useJUnitPlatform()
    }

    wrapper {
        gradleVersion = "8.12"
    }
}

detekt {
    config.setFrom(files("detekt.yml"))
    buildUponDefaultConfig = true
}

tasks.register<proguard.gradle.ProGuardTask>("obfuscate") {
    dependsOn("jar")
    
    val jarFile = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    val obfuscatedJar = layout.buildDirectory.file("obfuscated/${project.name}-${project.version}-obfuscated.jar")
    
    injars(jarFile)
    outjars(obfuscatedJar)
    
    // IntelliJ Platform classpath
    val platformClasspath = configurations.getByName("compileClasspath").files
    libraryjars(platformClasspath)
    
    // JDK modules
    val javaHome = System.getProperty("java.home")
    libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.base.jmod")
    libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.desktop.jmod")
    libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.xml.jmod")
    
    // ---- TRIM equivalent settings ----
    keepattributes("SourceFile,LineNumberTable")  // keep line numbers
    keepattributes("*Annotation*")  // keep annotations (IntelliJ needs them)
    keepattributes("Signature")  // keep generics info
    keepattributes("InnerClasses")  // keep inner class info
    keepattributes("Exceptions")  // keep exception attributes
    
    // ---- OBFUSCATE equivalent settings ----
    optimizationpasses(3)
    allowaccessmodification()
    repackageclasses("")
    overloadaggressively()
    
    // Keep all classes referenced in plugin.xml (extension points, actions, services, configurables)
    keep("public class * extends com.intellij.openapi.actionSystem.AnAction { *; }")
    keep("public class * implements com.intellij.openapi.components.PersistentStateComponent { *; }")
    keep("public class * implements com.intellij.openapi.options.Configurable { *; }")
    keep("public class * extends com.intellij.openapi.startup.ProjectActivity { *; }")
    keep("public class * implements com.intellij.openapi.wm.ToolWindowFactory { *; }")
    keep("public class * implements com.intellij.openapi.wm.StatusBarWidgetFactory { *; }")
    keep("public class * implements com.intellij.codeInsight.daemon.LineMarkerProvider { *; }")
    keep("public class * implements com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider { *; }")
    keep("public class * extends com.intellij.openapi.fileEditor.FileEditorProvider { *; }")
    keep("public class * implements com.intellij.openapi.project.ProjectManagerListener { *; }")
    keep("public class * extends com.intellij.lang.annotation.Annotator { *; }")
    keep("public class * implements com.intellij.psi.PsiElementFinder { *; }")
    keep("public class * extends com.intellij.openapi.startup.StartupActivity { *; }")
    keep("public class * implements com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener { *; }")
    keep("public class * implements com.intellij.openapi.vcs.checkin.CheckinHandler { *; }")
    keep("public class * extends com.intellij.openapi.vcs.checkin.CheckinHandlerFactory { *; }")
    keep("public class * implements com.intellij.openapi.vfs.newvfs.BulkFileListener { *; }")
    
    // Keep all project services (they're looked up by class)
    keep("public class * { public static * getInstance(com.intellij.openapi.project.Project); }")
    keep("public class * { public static * getInstance(); }")
    
    // Keep plugin entry points
    keep(mapOf("allowobfuscation" to false), "class ${project.group}.** { void runActivity(com.intellij.openapi.project.Project, kotlin.coroutines.Continuation); }")
    
    // Keep State classes for XML serialization
    keepclassmembers("class * { ** INSTANCE; }")
    keepclassmembers("class * extends com.intellij.openapi.components.PersistentStateComponent { *; }")
    
    // Keep Kotlin metadata
    keepattributes("RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations")
    keep("class kotlin.Metadata { *; }")
    
    // Keep resource bundle references
    keepclassmembers("class * { static java.util.ResourceBundle getBundle(...); }")
    
    // Don't warn about IntelliJ platform classes
    dontwarn("com.intellij.**")
    dontwarn("org.jetbrains.**")
    dontwarn("kotlin.**")
    dontwarn("kotlinx.**")
    
    // Verbose output
    printmapping(layout.buildDirectory.file("proguard/mapping.txt"))
    printseeds(layout.buildDirectory.file("proguard/seeds.txt"))
    printusage(layout.buildDirectory.file("proguard/usage.txt"))
}
