#!/bin/bash

set -e
set -o pipefail

clear
./gradlew clean codenarc
mvn -B -e clean install -PaddFiles -Pduplicates -DgroovydocDir=build/groovydoc -Dsilence
