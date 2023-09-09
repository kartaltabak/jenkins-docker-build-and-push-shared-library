#!/usr/bin/env groovy

def call(Map pipelineParams) {
    Map<String, String> defaultParams = [mainBranch           : 'main',
                                         mainBranchCron       : '@weekly',
                                         baseImage            : 'jenkins/jenkins:jdk11',
                                         registryServer       : 'https://registry-1.docker.io',
                                         registryCredentialsId: 'Dockerhub-kartaltabak',
                                         registryRepoName     : 'kartaltabak/jenkins-with-docker',
                                         dockerContextFolder  : 'docker',
                                         imageTestCommand     : 'docker --version']
    pipelineParams = defaultParams << pipelineParams

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
                            def repoName = registryRepoName
                            def taggedName = repoName + ":" + tag
                            def image = docker.build(taggedName, dockerContextFolder)

                            sh "docker run --rm ${taggedName} ${imageTestCommand}"

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
