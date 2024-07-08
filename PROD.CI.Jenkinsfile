pipeline {
    agent any

    environment {
        GIT_REPO = 'http://192.168.100.23/raad/backend/raad_backend.git'
        GIT_REPO_OTHER = 'git@192.168.100.23:raad/backend/raad_backend_other.git'
        GIT_CREDENTIALS_ID = 'Version_Controller_Credential'
        GIT_CREDENTIALS_ID_FOR_BUILDSERVER = 'Jenkins_Node_TO_Version_Controller_Credential'
        SONARQUBE_SERVER = 'http://192.168.100.26:9000'
        SONARQUBE_PROJECT_KEY = 'Raad-BackEnd-Raad_BackEnd'
        SONARQUBE_TOKEN = credentials('Code_Analyzer_Token')
        BUILT_IMAGE = ''
        APP_NAME = 'raad_backend'
        RELEASE = '1.0.0'
        IMAGE_NAME = '${NEXUS_REPO}'+'/'+'${APP_NAME}'
        IMAGE_TAG = "${RELEASE}-${BUILD_NUMBER}"
        NEXUS_REPO = '192.168.100.40:8083'
        NEXUS_CREDENTIALS_ID = 'Nexus_Docker_Private_Registry_Credential'
    }

    stages {
        stage('Clean Up The Local Workspace') {
            steps {
                cleanWs()
            }
        }
        
        stage('Clean Up The Buildserver Workspace') {
            agent { label 'Buildserver' }
            steps {
                cleanWs()
            }
        }

        stage('Checkout From SCM') {
            agent { label 'Buildserver' }
            steps {
                git branch: "main", credentialsId: "${env.GIT_CREDENTIALS_ID}", url: "${env.GIT_REPO}"                
            }
        }

        stage('Build And Scan The Java App') {
            agent { label 'gradle-8.8.0-jdk17-jammy' }
            steps {
                sh '''
                    gradle build -x test
                    '''
                stash includes: 'target/**', name: 'java-built-files'
                script {
                    def scannerHome = tool name: 'SonarQube Scanner', type: 'hudson.plugins.sonar.SonarRunnerInstallation'
                    withSonarQubeEnv('SonarQube') {
                        sh """
                            ${scannerHome}/bin/sonar-scanner \
                            -Dsonar.projectKey=${env.SONARQUBE_PROJECT_KEY} \
                            -Dsonar.sources=src/main/java/org/traccar \
                            -Dsonar.java.binaries=build/classes/java/main/org/traccar \
                            -Dsonar.host.url=${env.SONARQUBE_SERVER} \
                            -Dsonar.login=${env.SONARQUBE_TOKEN} \
                            -Dsonar.java.source=17
                        """
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 1, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Prepare Docker Build Environment') {
            agent { label 'Buildserver' }
            steps {
                unstash 'java-built-files'
                dir('Base-BackEnd') {
                    git branch: "main", credentialsId: "${env.GIT_CREDENTIALS_ID_FOR_BUILDSERVER}", url: "${env.GIT_REPO_OTHER}"
                }
                script {
                    sh 'cp -r target/* Base-BackEnd/'
                }
            }
        }
 
         stage('Build Docker Image') {
            agent { label 'Buildserver' }
            steps {
                dir('Base-BackEnd') {
                    script {
                        BUILT_IMAGE = docker.build APP_NAME
                    }
                }
            }
        }

        stage('Scan Docker Image') {
            agent { label 'Buildserver' }
            steps {
                script {
                    withEnv(['TRIVY_SKIP_DB_UPDATE=true']) {
                        // sh "trivy image --exit-code 1 --ignore-unfixed --severity HIGH,CRITICAL ${env.APP_NAME}"
                        sh "trivy image --ignore-unfixed --severity HIGH,CRITICAL ${env.APP_NAME}"
                    }
                }
                
            }
        }

        stage('Push Docker Image to Nexus') {
            agent { label 'Buildserver' }
            steps {
                script {
                    docker.withRegistry("http://"+NEXUS_REPO, NEXUS_CREDENTIALS_ID) {
                        BUILT_IMAGE.push(IMAGE_TAG)
                        BUILT_IMAGE.push("latest")
                    }
                }
            }
        }
    }

    post {
        success {
            echo 'Pipeline completed successfully!'
        }
        failure {
            echo 'Pipeline faild to complete'
        }
    }
}