#!/usr/bin/env groovy

def call(Map pipelineParams) {
    Map defaultParams = [
            mainBranch           : 'main',
            mainBranchCron       : '@monthly',
            baseImage            : null,
            registryServer       : 'https://registry-1.docker.io',
            registryCredentialsId: 'Dockerhub-kartaltabak',
            registryRepoName     : null,
            dockerContextFolder  : 'docker',
            imageTestCommand     : null,
            imageTestCommands    : null
    ]
    if (pipelineParams == null) {
        pipelineParams = defaultParams
    } else {
        pipelineParams = defaultParams << pipelineParams
    }

    if (pipelineParams.baseImage == null) {
        error("baseImage is required")
    }
    if (pipelineParams.registryRepoName == null) {
        error("registryRepoName is required")
    }
    if (pipelineParams.imageTestCommands == null) {
        pipelineParams.imageTestCommands = []
    }
    if (pipelineParams.imageTestCommand != null) {
        pipelineParams.imageTestCommands.add(0, imageTestCommand)
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
                        sh "docker pull ${pipelineParams.baseImage}"
                        docker.withRegistry(pipelineParams.registryServer, pipelineParams.registryCredentialsId) {
                            def repoName = pipelineParams.registryRepoName
                            def taggedName = repoName + ":" + tag
                            def image = docker.build(taggedName, pipelineParams.dockerContextFolder)

                            for(testCommand in pipelineParams.imageTestCommands){
                                sh "docker run --rm ${taggedName} $testCommand}"
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
