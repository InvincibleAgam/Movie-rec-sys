pipeline {
    agent {
        docker {
            image 'eclipse-temurin:17-jdk'
            args  '-v $HOME/.m2:/root/.m2'          // cache Maven deps across builds
        }
    }

    environment {
        DOCKER_IMAGE   = 'movie-atlas'
        DOCKER_TAG     = "${env.BUILD_NUMBER ?: 'latest'}"
        REGISTRY       = credentials('docker-registry-url')   // configure in Jenkins credentials
        REGISTRY_CREDS = credentials('docker-registry-creds') // username/password credential
    }

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {

        // ── 1. Checkout ─────────────────────────────────────────
        stage('Checkout') {
            steps {
                cleanWs()
                checkout scm
            }
        }

        // ── 2. Build ────────────────────────────────────────────
        stage('Build') {
            steps {
                sh 'chmod +x mvnw'
                sh './mvnw -B -q -DskipTests package'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                }
            }
        }

        // ── 3. Unit Tests ───────────────────────────────────────
        stage('Unit Tests') {
            steps {
                sh './mvnw -B test'
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml',
                          allowEmptyResults: true
                }
            }
        }

        // ── 4. Code Quality ─────────────────────────────────────
        stage('Code Quality') {
            steps {
                sh './mvnw -B checkstyle:check || true'   // non-blocking
            }
        }

        // ── 5. Docker Build ─────────────────────────────────────
        stage('Docker Build') {
            steps {
                sh "docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} ."
                sh "docker tag ${DOCKER_IMAGE}:${DOCKER_TAG} ${DOCKER_IMAGE}:latest"
            }
        }

        // ── 6. Docker Push ──────────────────────────────────────
        stage('Docker Push') {
            when {
                allOf {
                    branch 'main'
                    expression { env.REGISTRY != null }
                }
            }
            steps {
                sh """
                    echo \$REGISTRY_CREDS_PSW | docker login \$REGISTRY -u \$REGISTRY_CREDS_USR --password-stdin
                    docker tag ${DOCKER_IMAGE}:${DOCKER_TAG} \$REGISTRY/${DOCKER_IMAGE}:${DOCKER_TAG}
                    docker tag ${DOCKER_IMAGE}:latest        \$REGISTRY/${DOCKER_IMAGE}:latest
                    docker push \$REGISTRY/${DOCKER_IMAGE}:${DOCKER_TAG}
                    docker push \$REGISTRY/${DOCKER_IMAGE}:latest
                """
            }
        }

        // ── 7. Deploy to Staging ────────────────────────────────
        stage('Deploy to Staging') {
            when { branch 'main' }
            steps {
                echo '🚀 Deploying to staging environment...'
                // Uncomment when a staging server is configured:
                // sh 'docker-compose -f docker-compose.yml up -d'
            }
        }

        // ── 8. Deploy to Production ─────────────────────────────
        stage('Deploy to Production') {
            when { branch 'main' }
            steps {
                input message: 'Deploy to Production?', ok: 'Deploy'
                echo '🚀 Deploying to production environment...'
                // Uncomment when a production target is configured:
                // sh 'docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d'
            }
        }
    }

    post {
        success {
            echo '✅ Pipeline completed successfully!'
            // mail to: 'team@example.com',
            //      subject: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
            //      body: "Build passed. ${env.BUILD_URL}"
        }
        failure {
            echo '❌ Pipeline failed.'
            // mail to: 'team@example.com',
            //      subject: "FAILURE: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
            //      body: "Build failed. Check ${env.BUILD_URL}"
        }
        always {
            cleanWs()
        }
    }
}
