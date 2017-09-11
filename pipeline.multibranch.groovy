import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.regex.Matcher
import java.util.regex.Pattern


def getWorkspace() {
	//fix for branches with '/' in name. See https://issues.jenkins-ci.org/browse/JENKINS-30744
    pwd().replace("%2F", "_")
}

node {
  ws(getWorkspace()){
    def mvnHome = tool 'mvn'
    def antHome = tool 'ant'
    def workspace = pwd()
    env.JAVA_HOME = "${tool 'java8'}"
    env.MAVEN_OPTS = "-Xmx1500m"

    def gitCredentialsId = "HASH-HERE";
    def gitRepository = "https://PATH/REPO.git";
    
    if(env.BRANCH_NAME.startsWith('PR-')){
      currentBuild.result = 'ABORTED'
      print('To avoid dublicate builds Pull Request\'s jobs are disabled right now. I\'s not an error, just workaround to save the resources...')
      return
    }
    
    println "Environment:"
    bat 'set > env.txt' 
	for (String i : readFile('env.txt').split("\r?\n")) {
    	println i
	}
    
    
    stage('Checkout') {
      
      step([$class: 'WsCleanup', notFailBuild: false])
      
      def branchName = env.BRANCH_NAME
      if(branchName.startsWith('PR-')){
        branchName = 'pr/'+ env.CHANGE_ID
      }
      checkout([$class: 'GitSCM', 
            branches: [[name: branchName]], 
            poll: true, 
            doGenerateSubmoduleConfigurations: false, 
            extensions: [
                [$class: 'GitLFSPull']               
            ],
            userRemoteConfigs: [
                [credentialsId: "${gitCredentialsId}",
                 url: "${gitRepository}",
                 refspec: '+refs/heads/*:refs/remotes/origin/* +refs/pull-requests/*/from:refs/remotes/origin/pr/*'
                ]]
            ])
    }
    stage('Build') {
      try{
        def folder = new File( "${workspace}\\build.xml" )
        print folder
		if( folder.exists() ) {
            // if exists then its a ant-style project
          print 'ANT project\'s structure detected...'
          dir("${workspace}"){
            bat("""
                "${tool 'ant'}/bin/ant" compile
            """)
          }
        } else {
          print 'MAVEN project\'s structure detected...'
          bat(/"${mvnHome}\\bin\\mvn" clean install/)
        }
      }catch(err) {
        currentBuild.result = 'FAILURE'
        emailext (
                subject: "FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'", 
                body: '${SCRIPT, template="failed-build.template"}', 
                mimeType: 'text/html',
                attachLog: true , 
                to: 'test@test.com',
                recipientProviders: [[$class: 'CulpritsRecipientProvider']]
        )
      }
    }
    stage('Results') {
      try{
      	junit '**/target/surefire-reports/TEST-*.xml'
      }catch(err){
        echo "There are not tests results"
      }
	  // Archive the build output artifacts.
      archiveArtifacts artifacts: 'PATH/target/*.zip'
      
      step([$class: 'WsCleanup', notFailBuild: true])
    }
  }
}