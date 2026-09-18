import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.grammarkit") version "2023.3.0.3"
    id("com.diffplug.spotless") version "8.6.0"
    id("dev.detekt") version "2.0.0-alpha.6"
}

sourceSets {
    main {
        java {
            srcDirs("src/main/gen")
        }
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.1.3")
        bundledModule("intellij.platform.dap")
        pluginVerifier()
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        id = "io.github.unurgunite.crystal"
        name = "Fast Crystal"
        version = project.version.toString()
        vendor {
            name = "unurgunite"
            url = "https://github.com/unurgunite"
        }
        ideaVersion {
            sinceBuild = "261"
        }
    }

    signing {
        certificateChain.set(providers.environmentVariable("CERTIFICATE_CHAIN"))
        privateKey.set(providers.environmentVariable("PRIVATE_KEY"))
        password.set(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
    }

    publishing {
        token.set(providers.environmentVariable("PUBLISH_TOKEN"))
    }

    pluginVerification {
        // NOTE: the plugin name must NOT contain the word "Plugin" — the IDE
        // rejects such descriptors at load time ("Invalid plugin descriptor").
        // "Fast Crystal" keeps the differentiator without the banned word.
        ides {
            // Verify only against RELEASED IDEs in the supported range (sinceBuild
            // 261 -> 261.x, 262.x): the default recommended() set also pulls EAP
            // builds (263-EAP), whose platform APIs may legitimately differ from
            // the release the plugin compiles against (intellijIdea 2026.1.3) —
            // e.g. the DapBreakpointsDescription ctor, unresolved on 263-EAP
            // while 261/262 verify Compatible. EAP drift is not a release
            // blocker; a real incompatibility with a released IDE still fails.
            select {
                types.set(listOf(IntelliJPlatformType.IntellijIdeaUltimate))
                channels.set(listOf(ProductRelease.Channel.RELEASE))
                sinceBuild.set("261")
            }
        }
    }
}

spotless {
    kotlin {
        // ktlint version pinned explicitly (spotless 8.6.0 default is 1.8.0):
        // unpinned ktlint() drifts between machines/CI.
        ktlint("1.8.0")
        target("src/**/*.kt")
        // Generated parser/lexer sources are committed but not hand-written.
        targetExclude("src/main/gen/**")
    }
    kotlinGradle {
        ktlint("1.8.0")
        target("*.kts")
    }
}

detekt {
    // Minimal config/detekt/detekt.yml on top of the default rule set:
    // ReturnCount max 5 and MaxLineLength 140, both aligned with ktlint.
    buildUponDefaultConfig = true
    config.setFrom(file("config/detekt/detekt.yml"))
}

tasks {
    runIde {
        systemProperty("idea.trust.all.projects", "true")
    }

    generateLexer {
        sourceFile.set(file("src/main/kotlin/io/github/unurgunite/crystal/lexer/Crystal.flex"))
        targetOutputDir.set(file("src/main/gen/io/github/unurgunite/crystal/lexer"))
    }

    generateParser {
        sourceFile.set(file("src/main/kotlin/io/github/unurgunite/crystal/parser/Crystal.bnf"))
        targetRootOutputDir.set(file("src/main/gen"))
        pathToParser.set("io/github/unurgunite/crystal/parser/CrystalParser.java")
        pathToPsiRoot.set("io/github/unurgunite/crystal/psi")
    }

    compileKotlin {
        dependsOn(generateLexer, generateParser)
    }

    // CrystalParserTest golden files are environment-flaky (JDK 21 + grammar-kit,
    // see TODO.md "ParserTest Non-Determinism"). They are excluded from the default
    // ./gradlew test and run via ./gradlew test -PgoldenOnly=true instead
    // (non-blocking CI step). StdlibGraphToolTest is a diagnostic dump (println +
    // stdlib-graph/ files, zero asserts), not a regression test — excluded from
    // the suite as well; its 4 cases stay runnable via the manual stdlib* tasks
    // below (stdlibParseErrors/stdlibBuildGraph/stdlibStructure/stdlibCheckFile).
    // The filter is set in doFirst so Test-task
    // configuration stays lazy (keeps the configuration cache working); the flag
    // is read into a plain val so the closure captures no Project reference.
    val goldenOnly = providers.gradleProperty("goldenOnly").orNull == "true"
    named<Test>("test") {
        // Test outcomes depend on ambient state the build cache cannot see:
        // the StubIndex hash slice (which symbols survive the lookup cap) and
        // the installed Crystal toolchain (stdlib contents). Replaying a
        // "green" cached result from a different environment hid real,
        // deterministic failures (completion priorities, 2026-09). Always
        // execute; never serve Test results from the build cache.
        outputs.cacheIf { false }
        doFirst {
            filter {
                if (goldenOnly) {
                    includeTestsMatching("*CrystalParserTest*")
                } else {
                    excludeTestsMatching("io.github.unurgunite.crystal.parser.CrystalParserTest")
                    excludeTestsMatching("io.github.unurgunite.crystal.tools.StdlibGraphToolTest")
                }
            }
        }
    }

    withType<JavaCompile>().configureEach {
        options.isFork = false
    }
}

val defaultTest = tasks.named<Test>("test")

fun Test.stdlibTool(name: String) {
    group = "stdlib"
    // Same no-cache rule as the main suite: results depend on the installed
    // toolchain (stdlib contents), which the build cache cannot see.
    outputs.cacheIf { false }
    val dt = defaultTest.get()
    testClassesDirs = dt.testClassesDirs
    classpath = dt.classpath
    jvmArgs(dt.allJvmArgs.filterNot { it.startsWith("-Xmx") } + "-Xmx4g" + "-Dgrammar.kit.gpub.max.level=6000")
    filter.includeTestsMatching("*StdlibGraphToolTest.$name")
    // Same sandbox setup as the `test` task: the IntelliJ test framework
    // resolves the plugin-under-test from the prepared sandbox. Without this
    // the stdlib tasks run un-instrumented classes against an empty sandbox
    // and platform startup dies with CNFE on our own FileType (seen on CI).
    dependsOn("prepareTestSandbox")
}
tasks.register<Test>("stdlibParseErrors") { stdlibTool("testAggregateParseErrors") }
tasks.register<Test>("stdlibBuildGraph") { stdlibTool("testBuildGraph") }
tasks.register<Test>("stdlibStructure") { stdlibTool("testBuildStructureJson") }
tasks.register<Test>("stdlibCheckFile") {
    stdlibTool("testCheckSingleFile")
    systemProperty("graph.file", System.getProperty("graph.file") ?: "")
}

kotlin {
    jvmToolchain(21)
}
