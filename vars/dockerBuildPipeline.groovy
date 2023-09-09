#!/usr/bin/env groovy

def call(Map pipelineParams) {
    String cron_string = BRANCH_NAME == "main" ? "@weekly" : ""

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
                        sh 'docker pull jenkins/jenkins:jdk11'
                        docker.withRegistry('https://registry-1.docker.io', 'Dockerhub-kartaltabak') {
                            def repoName = "kartaltabak/jenkins-with-docker"
                            def taggedName = repoName + ":" + tag
                            def image = docker.build(taggedName, "docker")

                            sh "docker run --rm ${taggedName} docker --version"

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
