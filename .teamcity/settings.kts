import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.PullRequests
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.SSHUpload
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.dotnetPublish
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.dotnetTest
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.sshExec
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.sshUpload
import jetbrains.buildServer.configs.kotlin.v2019_2.sharedResource
import jetbrains.buildServer.configs.kotlin.v2019_2.sharedResources
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.GitVcsRoot

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2021.2"

project {

    vcsRoot(HttpsGithubComYzhu228carvedRockWebsiteTestGitRefsHeadsMain)

    buildType(TestStaging)
    buildType(BuildWebsite)
    buildType(BuildTest)
    buildType(DeployProduction)
    buildType(DeployStaging)

    features {
        sharedResource {
            id = "PROJECT_EXT_2"
            name = "StagingEnvironment"
            resourceType = quoted(1)
        }
    }
}

object BuildTest : BuildType({
    name = "Build: Test"
    description = "Build tests"

    artifactRules = "bin/Release/net6.0 => WebSiteTester"

    vcs {
        root(HttpsGithubComYzhu228carvedRockWebsiteTestGitRefsHeadsMain)
    }

    steps {
        dotnetPublish {
            projects = "carved-rock-website-test.csproj"
            configuration = "Release"
            sdk = "6"
            param("dotNetCoverage.dotCover.home.path", "%teamcity.tool.JetBrains.dotCover.CommandLineTools.DEFAULT%")
        }
    }
})

object BuildWebsite : BuildType({
    name = "Build: Website"

    artifactRules = "public => website"

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        script {
            name = "Delete public directory (if exist)"
            enabled = false
            scriptContent = """sh -c "[ -d public ] && rm -r public""""
        }
        script {
            name = "Build Website"
            scriptContent = "hugo"
        }
    }

    triggers {
        vcs {
            branchFilter = """
                +:pull/*
                -:<default>
                -:develop
            """.trimIndent()
            perCheckinTriggering = true
            groupCheckinsByCommitter = true
            enableQueueOptimization = false
        }
    }

    features {
        pullRequests {
            vcsRootExtId = "${DslContext.settingsRoot.id}"
            provider = github {
                authType = token {
                    token = "credentialsJSON:4fea1804-e3f4-4121-a645-e020b96c3f92"
                }
                filterTargetBranch = "+:refs/heads/master"
                filterAuthorRole = PullRequests.GitHubRoleFilter.MEMBER
            }
        }
        commitStatusPublisher {
            vcsRootExtId = "${DslContext.settingsRoot.id}"
            publisher = github {
                githubUrl = "https://api.github.com"
                authType = personalToken {
                    token = "credentialsJSON:7b769fc7-b7c1-4bf3-9ecb-42f718b3a41b"
                }
            }
        }
    }
})

object DeployProduction : BuildType({
    name = "Deploy: Production"

    enablePersonalBuilds = false
    type = BuildTypeSettings.Type.DEPLOYMENT
    maxRunningBuilds = 1

    params {
        param("deployment_ip_address", "3.82.144.44")
        param("deployment_html_folder", "/var/www/html")
    }

    vcs {
        branchFilter = "+:<default>"
    }

    steps {
        sshExec {
            name = "Delete files"
            commands = "rm -rf %deployment_html_folder%/*"
            targetUrl = "%deployment_ip_address%"
            authMethod = uploadedKey {
                username = "ec2-user"
                key = "carved-rock-website"
            }
        }
        sshUpload {
            name = "Upload files"
            transportProtocol = SSHUpload.TransportProtocol.SCP
            sourcePath = "."
            targetUrl = "%deployment_ip_address%:%deployment_html_folder%"
            authMethod = uploadedKey {
                username = "ec2-user"
                key = "carved-rock-website"
            }
        }
    }

    dependencies {
        snapshot(TestStaging) {
            reuseBuilds = ReuseBuilds.ANY
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        artifacts(BuildWebsite) {
            cleanDestination = true
            artifactRules = "+:website"
        }
    }
})

object DeployStaging : BuildType({
    name = "Deploy: Staging"

    enablePersonalBuilds = false
    type = BuildTypeSettings.Type.DEPLOYMENT
    maxRunningBuilds = 1

    params {
        param("deployment_ip_address", "3.86.39.26")
        param("deployment_html_folder", "/var/www/html")
    }

    vcs {
        branchFilter = "+:<default>"
    }

    steps {
        sshExec {
            name = "Delete files"
            commands = "rm -rf %deployment_html_folder%/*"
            targetUrl = "%deployment_ip_address%"
            authMethod = uploadedKey {
                username = "ec2-user"
                key = "carved-rock-website"
            }
        }
        sshUpload {
            name = "Upload files"
            transportProtocol = SSHUpload.TransportProtocol.SCP
            sourcePath = "."
            targetUrl = "%deployment_ip_address%:%deployment_html_folder%"
            authMethod = uploadedKey {
                username = "ec2-user"
                key = "carved-rock-website"
            }
        }
    }

    triggers {
        vcs {
            enabled = false
            branchFilter = "+:<default>"
            watchChangesInDependencies = true
            perCheckinTriggering = true
            groupCheckinsByCommitter = true
            enableQueueOptimization = false
        }
    }

    features {
        sharedResources {
            writeLock("StagingEnvironment")
        }
    }

    dependencies {
        artifacts(BuildWebsite) {
            cleanDestination = true
            artifactRules = "+:website"
        }
    }
})

object TestStaging : BuildType({
    name = "Test: Staging"

    vcs {
        branchFilter = "+:<default>"
        showDependenciesChanges = true
    }

    steps {
        dotnetTest {
            name = "Test Staging"
            projects = "carved-rock-website-test.dll"
            param("dotNetCoverage.dotCover.home.path", "%teamcity.tool.JetBrains.dotCover.CommandLineTools.DEFAULT%")
        }
    }

    triggers {
        vcs {
            branchFilter = ""
            watchChangesInDependencies = true
            perCheckinTriggering = true
            groupCheckinsByCommitter = true
            enableQueueOptimization = false
        }
    }

    features {
        sharedResources {
            writeLock("StagingEnvironment")
        }
    }

    dependencies {
        dependency(BuildTest) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }

            artifacts {
                cleanDestination = true
                artifactRules = "WebSiteTester"
            }
        }
        snapshot(DeployStaging) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }
})

object HttpsGithubComYzhu228carvedRockWebsiteTestGitRefsHeadsMain : GitVcsRoot({
    name = "https://github.com/yzhu228/carved-rock-website-test.git#refs/heads/main"
    url = "https://github.com/yzhu228/carved-rock-website-test.git"
    branch = "refs/heads/main"
    authMethod = password {
        userName = "yzhu"
        password = "credentialsJSON:6643ae94-31af-4297-a7a7-ff794ca023dc"
    }
})
