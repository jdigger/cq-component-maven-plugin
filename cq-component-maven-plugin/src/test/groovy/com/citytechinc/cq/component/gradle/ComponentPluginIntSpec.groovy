package com.citytechinc.cq.component.gradle

import groovy.transform.CompileStatic
import nebula.test.IntegrationSpec
import org.gradle.api.logging.LogLevel

import java.nio.file.Files
import java.nio.file.Path

class ComponentPluginIntSpec extends IntegrationSpec {

    def setup() {
        createFile("test-init.gradle", projectDir) << """
            startParameter.offline=true
            // startParameter.profile=true

            apply plugin: TestRepositoryPlugin

            class TestRepositoryPlugin implements Plugin<Gradle> {
                void apply(Gradle gradle) {
                    gradle.allprojects{ project ->
                        buildscript {
                            repositories {
                                maven { url '${testLibsPath()}' }
                                flatDir { dir '${testLibsPath()}' }
                            }
                        }
                        project.repositories {
                            // Remove all repositories not pointing to the testing repository url
                            all { ArtifactRepository repo ->
                                if ((repo instanceof MavenArtifactRepository) && (!repo.url.toString().contains("test-libs"))) {
                                    project.logger.lifecycle "Repository \${repo.url} removed. Only test repo allowed"
                                    remove repo
                                }
                            }

                            maven { url '${testLibsPath()}' }
                            flatDir { dir '${testLibsPath()}' }
                        }
                    }
                }
            }
        """.stripIndent()
        addInitScript(new File(projectDir, "test-init.gradle"))
    }

    @CompileStatic
    static Path testLibsPath() throws IOException {
        return topLevelDir().resolve("test-libs")
    }


    @CompileStatic
    static Path topLevelDir() throws IOException {
        return getTopLevelDir(new File(".").getCanonicalFile().toPath())
    }


    @CompileStatic
    private static Path getTopLevelDir(Path dir) throws IOException {
        if (dir == null) throw new IllegalArgumentException("dir == null")
        return (isTopLevelDir(dir)) ? dir : getTopLevelDir(dir.getParent())
    }


    @CompileStatic
    private static boolean isTopLevelDir(Path dir) throws IOException {
        if (dir == null) throw new IllegalArgumentException("dir == null")
        return Files.walk(dir, 1).anyMatch({ ("LICENSE" == it.getFileName().toString()) })
    }


    def "basic test"() {
        logLevel = LogLevel.LIFECYCLE
        writeComponentHelloWorld('com.citytechinc.cq.component.gradle.test')
        settingsFile << """
            includeBuild '${topLevelDir()}'
        """.stripIndent()

        buildFile << """
            apply plugin: 'java'
            apply plugin: '${ComponentPlugin.PLUGIN_ID}'

            def annJar = gradle.includedBuild('cq-component-plugin').task(':cq-component-annotations:jar')

            task createPackage(type: Zip) {
                dependsOn annJar
                dependsOn jar
                from jar.archivePath
            }

            componentPlugin {
                componentPathBase = "jcr_root/apps/myProduct/components/"
                // componentPathSuffix = "content"
                // defaultComponentGroup = "myProduct"
                transformerName = "camel-case"
            }

            generateComponents.dependsOn "createPackage"
            generateComponents.archiveFile = createPackage.archivePath

            dependencies {
                compile fileTree('${topLevelDir()}/cq-component-annotations/build/libs')
                componentPlugin files(project.compileJava.destinationDir)
            }
        """.stripIndent()

        def result
        when:
        // gradleVersion = "4.0"
        result = runTasks(':clean', ':generateComponents')

        then:
        result.rethrowFailure()
        result.wasExecuted(":generateComponents")
        fileExists('build/classes/java/main/com/citytechinc/cq/component/gradle/test/HelloWorldComponent.class')
        fileExists('build/tempComponentConfig/jcr_root/apps/myProduct/components/content/helloworld/.content.xml')
        fileExists('build/tempComponentConfig/jcr_root/apps/myProduct/components/content/helloworld/_cq_dialog.xml')
        fileExists('build/tempComponentConfig/jcr_root/apps/myProduct/components/content/helloworld/_cq_editConfig.xml')
        fileExists('build/tempComponentConfig/jcr_root/apps/myProduct/components/content/helloworld/dialog.xml')

        println result?.standardOutput
        println result?.standardError

        when:
        // gradleVersion = "4.0"
        result = runTasks(':generateComponents', "-x", ":createPackage")

        then:
        result.rethrowFailure()
        result.wasSkipped(":generateComponents")

        cleanup:
        println result?.standardOutput
        println result?.standardError
    }


    @CompileStatic
    protected File writeComponentHelloWorld(String packageDotted, File baseDir = getProjectDir()) {
        def path = 'src/main/java/' + packageDotted.replace('.', '/') + '/HelloWorldComponent.java'
        def srcFile = createFile(path, baseDir)
        srcFile << """
            package ${packageDotted};

            import com.citytechinc.cq.component.annotations.Component;
            import com.citytechinc.cq.component.annotations.DialogField;
            import com.citytechinc.cq.component.annotations.widgets.PathField;
            import com.citytechinc.cq.component.annotations.widgets.TextField;

            @Component(value = "Hello World",
                name = "helloworld",
                allowedParents = "*/parsys",
                resourceSuperType = "foundation/components/parbase")
            public interface HelloWorldComponent {
                @PathField(rootPath = "/content/")
                @DialogField(fieldLabel = "Where is the world?",
                    fieldDescription = "The path to find the world",
                    required = true)
                String getWorldPath();

                @TextField
                @DialogField(fieldLabel = "Greeting",
                    fieldDescription = "How to greet people",
                    value = "Hello")
                public String getGreeting();
            }
        """.stripIndent()
        return srcFile
    }

}
