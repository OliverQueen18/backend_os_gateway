pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        choice(
            name: 'ENV',
            choices: ['prod', 'test', 'dev'],
            description: 'Environnement de déploiement'
        )
        booleanParam(
            name: 'SKIP_TESTS',
            defaultValue: true,
            description: 'Ignorer les tests Maven'
        )
        booleanParam(
            name: 'PUSH_DOCKER',
            defaultValue: true,
            description: 'Pousser les images Docker Hub'
        )
        booleanParam(
            name: 'DEPLOY',
            defaultValue: true,
            description: 'Déployer sur le VPS (compose pull + up)'
        )
        string(
            name: 'DEPLOY_HOST',
            defaultValue: 'adminubuntu@osgateway.olive-services.net',
            description: 'SSH user@host du VPS'
        )
        string(
            name: 'DEPLOY_PATH',
            defaultValue: '/home/adminubuntu/OliveApps/OSGATEWAY/backend',
            description: 'Chemin du repo backend sur le VPS'
        )
        string(
            name: 'ENV_FILE',
            defaultValue: '/home/adminubuntu/OliveApps/prod.env',
            description: 'Fichier env OliveApps (contient le bloc OSGATEWAY_*)'
        )
    }

    environment {
        APP_NAME       = 'backend-osgateway'
        DOCKER_NS      = 'oliverqueen18'
        IMAGE_PREFIX   = 'osgateway'
        DOCKER_TAG     = "${BUILD_NUMBER}"
        DOCKER_BUILDKIT = '1'
        // Services Spring Boot (images Docker Hub)
        OSG_SERVICES   = 'auth-service user-service gateway-service ussd-service sms-service transaction-service notification-service audit-service reporting-service scheduler-service monitoring-service api-gateway'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Maven Package') {
            steps {
                sh """
                if [ '${params.SKIP_TESTS}' = 'true' ]; then
                  mvn -B -DskipTests package
                else
                  mvn -B package
                fi
                """
            }
        }

        stage('Docker Build') {
            steps {
                sh """
                set -e
                for svc in ${OSG_SERVICES}; do
                  IMG="${DOCKER_NS}/${IMAGE_PREFIX}-\${svc}"
                  echo "=== Building \${IMG}:${DOCKER_TAG} ==="
                  docker pull "\${IMG}:latest" || true
                  docker build \
                    --build-arg BUILDKIT_INLINE_CACHE=1 \
                    --cache-from "\${IMG}:latest" \
                    -t "\${IMG}:${DOCKER_TAG}" \
                    -t "\${IMG}:latest" \
                    "./\${svc}"
                done
                """
            }
        }

        stage('Docker Push') {
            when {
                expression { params.PUSH_DOCKER }
            }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-credentials',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh """
                    set -e
                    echo "\$DOCKER_PASS" | docker login -u "\$DOCKER_USER" --password-stdin
                    for svc in ${OSG_SERVICES}; do
                      IMG="${DOCKER_NS}/${IMAGE_PREFIX}-\${svc}"
                      docker push "\${IMG}:${DOCKER_TAG}"
                      docker push "\${IMG}:latest"
                    done
                    """
                }
            }
        }

        stage('Deploy VPS') {
            when {
                expression { params.DEPLOY }
            }
            steps {
                sshagent(credentials: ['oliveapps-ssh']) {
                    sh """
                    set -e
                    ssh -o StrictHostKeyChecking=no ${params.DEPLOY_HOST} bash -s <<ENDSSH
set -e
cd ${params.DEPLOY_PATH}
git pull --ff-only || true
if [ -f "${params.ENV_FILE}" ]; then
  grep -E '^(OSGATEWAY_|POSTGRES_|JWT_|RABBITMQ_|JAVA_TOOL_|RATE_LIMIT_|FIREBASE_|GRAFANA_)' "${params.ENV_FILE}" > .env.osg.tmp || true
  if [ -s .env.osg.tmp ]; then
    sed -E \\
      -e 's/^OSGATEWAY_DB=/POSTGRES_DB=/' \\
      -e 's/^OSGATEWAY_DB_USER=/POSTGRES_USER=/' \\
      -e 's/^OSGATEWAY_DB_PASS=/POSTGRES_PASSWORD=/' \\
      -e 's/^OSGATEWAY_JWT_SECRET=/JWT_SECRET=/' \\
      -e 's/^OSGATEWAY_RABBITMQ_USER=/RABBITMQ_USER=/' \\
      -e 's/^OSGATEWAY_RABBITMQ_PASS=/RABBITMQ_PASSWORD=/' \\
      .env.osg.tmp > .env
    rm -f .env.osg.tmp
  fi
fi
export OSG_TAG=${DOCKER_TAG}
docker compose pull || true
docker compose up -d --remove-orphans
docker image prune -f || true
ENDSSH
                    """
                }
            }
        }
    }

    post {
        success {
            echo "✅ ${APP_NAME} OK — tag ${DOCKER_TAG} (ENV=${params.ENV})"
        }
        failure {
            echo "❌ ${APP_NAME} échoué"
        }
        always {
            cleanWs(deleteDirs: true, notFailBuild: true)
        }
    }
}
