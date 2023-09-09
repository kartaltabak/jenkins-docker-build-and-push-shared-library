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
                imageTestCommands    : null
        ]
        defaultImageBuilder << imageBuilder
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
                            if (imageBuilder.baseImage == null) {
                                error("baseImage is required")
                            }
                            if (imageBuilder.registryRepoName == null) {
                                error("registryRepoName is required")
                            }
                            if (imageBuilder.imageTestCommands == null) {
                                imageBuilder.imageTestCommands = []
                            }
                            if (imageBuilder.imageTestCommand != null) {
                                imageBuilder.imageTestCommands.add(0, imageBuilder.imageTestCommand)
                            }

                            sh "docker pull ${imageBuilder.baseImage}"
                            docker.withRegistry(imageBuilder.registryServer, imageBuilder.registryCredentialsId) {
                                def repoName = imageBuilder.registryRepoName
                                def taggedName = repoName + ":" + tag
                                def image = docker.build(taggedName, imageBuilder.dockerContextFolder)

                                for (testCommand in imageBuilder.imageTestCommands) {
                                    sh "docker run --rm ${taggedName} ${testCommand}"
                                }

                                image.push()

                                def latestName = repoName + ":latest"
                                sh "docker tag " + taggedName + " " + latestName
                                docker.image(latestName).push()
                            }
                        }
                    }
                }
            }
        }
    }
}
