#!/usr/bin/env groovy

def call(Map pipelineParams) {
    Map defaultParams = [
            mainBranch    : 'main',
            mainBranchCron: '@monthly',
            imageBuilder  : null,
            imageBuilders : null
    ]
    if (pipelineParams == null) {
        pipelineParams = defaultParams
    } else {
        pipelineParams = defaultParams << pipelineParams
    }

    if (pipelineParams.imageBuilders == null) {
        pipelineParams.imageBuilders = []
    }
    if (pipelineParams.imageBuilder != null) {
        pipelineParams.imageBuilders.add(0, pipelineParams.imageBuilder)
    }

    pipelineParams.imageBuilders = pipelineParams.imageBuilders.collect { imageBuilder ->
        defaultImageBuilder = [
                baseImage            : null,
                registryServer       : 'https://registry-1.docker.io',
                registryCredentialsId: 'Dockerhub-kartaltabak',
                registryRepoName     : null,
                dockerContextFolder  : 'docker',
                imageTestCommand     : null,
                imageTestCommands    : null,
                timestampTag         : null,
                tagCommand           : null,
                latestTag            : 'latest'
        ]
        defaultImageBuilder << imageBuilder
    }

    if (pipelineParams.imageBuilders.size() == 0) {
        error("imageBuilders is required")
    }

    String cron_string = BRANCH_NAME == pipelineParams.mainBranch ? pipelineParams.mainBranchCron : ""

    tag = new Date().format("yyyyMMdd", TimeZone.getTimeZone('UTC'))

    pipeline {
        agent any
        options {
            disableConcurrentBuilds()
        }
        triggers { cron(cron_string) }
        stages {
            stage('Build & Push') {
                steps {
                    script {
                        for (imageBuilder in pipelineParams.imageBuilders) {
                            if (imageBuilder.registryRepoName == null) {
                                error("registryRepoName is required")
                            }
                            if (imageBuilder.imageTestCommands == null) {
                                imageBuilder.imageTestCommands = []
                            }
                            if (imageBuilder.imageTestCommand != null) {
                                imageBuilder.imageTestCommands.add(0, imageBuilder.imageTestCommand)
                            }

                            if (imageBuilder.baseImage != null) {
                                sh "docker pull ${imageBuilder.baseImage}"
                            }
                            docker.withRegistry(imageBuilder.registryServer, imageBuilder.registryCredentialsId) {
                                def repoName = imageBuilder.registryRepoName
                                def taggedName
                                if (imageBuilder.timestampTag != null) {
                                    taggedName = repoName + ":" + imageBuilder.timestampTag + "-" + tag
                                } else {
                                    taggedName = repoName + ":" + tag
                                }
                                def image = docker.build(taggedName, "--no-cache ${imageBuilder.dockerContextFolder}")

                                for (testCommand in imageBuilder.imageTestCommands) {
                                    sh "docker run --rm ${taggedName} ${testCommand}"
                                }

                                image.push()

                                if (imageBuilder.tagCommand != null) {
                                    def customTag = sh(
                                            returnStdout: true,
                                            script: "docker run --rm --entrypoint bash ${taggedName} ${imageBuilder.tagCommand}"
                                    ).trim()
                                    def customName = (repoName + ":" + customTag)
                                    sh "docker tag ${taggedName} ${customName}"
                                    echo "Pushing \"${customName}\""
                                    docker.image(customName).push()
                                }

                                def latestName = repoName + ":" + imageBuilder.latestTag
                                sh "docker tag ${taggedName} ${latestName}"
                                docker.image(latestName).push()
                            }
                        }
                    }
                }
            }
        }
    }
}
