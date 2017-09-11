import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.regex.Matcher
import java.util.regex.Pattern

node {
    def mvnHome = tool 'mvn'
    env.JAVA_HOME = "${tool 'java8'}"
    env.MAVEN_OPTS = "-Xmx1500m"

    def gitCredentialsId = "HASH-HERE";
    def gitRepository = "http://server/repo.git";
    
	// clean workspace before doing anything
    step([$class: 'WsCleanup', notFailBuild: true])
    
    stage('Checkout') {
        // git checkout
        checkout([$class: 'GitSCM', 
            branches: [[name: 'master']], 
            poll: true, 
            doGenerateSubmoduleConfigurations: false, 
            userRemoteConfigs: [
                [credentialsId: "${gitCredentialsId}",
                 url: "${gitRepository}"]]
            ])
    }
    stage('Build') {
        try{
            bat(/"${mvnHome}\\bin\\mvn" -Dbuild.number=${currentBuild.number} clean package/)
        }catch(err) {
            currentBuild.result = 'FAILURE'
            emailext (
                subject: "FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'", 
                body: '${SCRIPT, template="failed-build.email.template"}', 
                mimeType: 'text/html',
                attachLog: true
            )
        }
    }
    stage('Results') {
        try{
            junit '**/target/surefire-reports/TEST-*.xml'
        }catch(err){
            echo "There are not tests results"
        }
        archive 'path/target/*.jar'
		
		//cleanup workspace after the build
        step([$class: 'WsCleanup', notFailBuild: true])
    }
}