#!/bin/bash

set -e
set -o pipefail

version=0.2.6
# http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.apache.maven.plugins%22%20AND%20a%3A%22maven-gpg-plugin%22
command="mvn org.apache.maven.plugins:maven-gpg-plugin:1.4:sign-and-deploy-file -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging -Dgpg.passphrase=$gpgPassphrase"
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
