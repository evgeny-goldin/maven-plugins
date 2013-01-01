#!/bin/bash

clear
./gradlew clean codenarc
mvn -B -e clean install -PaddFiles -Pduplicates -DgroovydocDir=~/Temp/groovydoc
