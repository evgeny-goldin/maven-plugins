#!/bin/bash

# http://stackoverflow.com/questions/821396/bash-possible-to-abort-shell-script-if-any-command-returns-a-non-zero-value
set -e
set -o pipefail

version=0.2.5
command="mvn gpg:sign-and-deploy-file -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging -Dgpg.passphrase=$gpgPassphrase"
rootDir=`pwd`

for module in '.' 'mojo-parent'
do
    cd $rootDir/$module
    echo "[<===[`pwd`]===>]"
    $command -DpomFile=pom.xml -Dfile=pom.xml
done

for module in 'about-maven-plugin' 'assert-maven-plugin' 'copy-maven-plugin' 'duplicates-finder-plugin' 'find-maven-plugin' 'ivy-maven-plugin' 'jenkins-maven-plugin' 'mail-maven-plugin'  'maven-common' 'properties-maven-plugin' 'spring-batch-maven-plugin' 'sshexec-maven-plugin' 'timestamp-maven-plugin'
do
    cd $rootDir/$module/target
    echo "[<===[`pwd`]===>]"
    $command -DpomFile=../pom.xml -Dfile=$module-$version.jar -Dfiles=$module-$version-javadoc.jar,$module-$version-sources.jar -Dclassifiers=javadoc,sources -Dtypes=jar,jar
done
