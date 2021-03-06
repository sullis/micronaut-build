package io.micronaut.build

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

/**
 * Micronaut internal Gradle plugin. Not intended to be used in user's projects.
 */
class MicronautDependencyUpdatesPlugin implements Plugin<Project> {


    public static final String GRADLE_VERSIONS_PLUGIN = "com.github.ben-manes.versions"
    public static final String USE_LATEST_VERSIONS_PLUGIN = "se.patrikerdes.use-latest-versions"

    @Override
    void apply(Project project) {
        project.apply plugin: GRADLE_VERSIONS_PLUGIN
        project.apply plugin: USE_LATEST_VERSIONS_PLUGIN

        project.afterEvaluate {
            MicronautBuildExtension micronautBuildExtension
            if (project.extensions.findByType(MicronautBuildExtension)) {
                micronautBuildExtension = project.extensions.getByType(MicronautBuildExtension)
            } else {
                micronautBuildExtension = project.extensions.create('micronautBuild', MicronautBuildExtension)
            }

            project.configurations.all { Configuration cfg ->
                if (micronautBuildExtension.resolutionStrategy) {
                    cfg.resolutionStrategy(micronautBuildExtension.resolutionStrategy)
                }
            }

            project.with {
                dependencyUpdates {
                    checkForGradleUpdate = true
                    gradleReleaseChannel = "current"
                    checkConstraints = true
                    revision = "release"
                    rejectVersionIf { mod ->
                        mod.candidate.version ==~
                                micronautBuildExtension.dependencyUpdatesPattern ||
                                ['alpha', 'beta', 'milestone', 'rc', 'cr', 'm', 'preview', 'b', 'ea'].any { qualifier ->
                                    mod.candidate.version ==~ /(?i).*[.-]$qualifier[.\d-+]*/
                                } ||
                                mod.candidate.group == 'io.micronaut' // managed by the micronaut version
                    }

                    outputFormatter = { result ->
                        if (!result.outdated.dependencies.isEmpty()) {
                            def upgradeVersions = result.outdated.dependencies
                            if (!upgradeVersions.isEmpty()) {
                                println "\nThe following dependencies have later ${revision} versions:"
                                upgradeVersions.each { dep ->
                                    def currentVersion = dep.version
                                    println " - ${dep.group}:${dep.name} [${currentVersion} -> ${dep.available[revision]}]"
                                    if (dep.projectUrl != null) {
                                        println "     ${dep.projectUrl}"
                                    }
                                }
                                throw new GradleException('Abort, there are dependencies to update. Run ./gradlew useLatestVersions to update them in place')
                            }
                        }
                    }
                }

                useLatestVersions {
                    updateRootProperties = true
                }

                if (tasks.findByName("checkstyleMain")) {
                    tasks.getByName("checkstyleMain").dependsOn('dependencyUpdates')
                }
            }
        }
    }
}
