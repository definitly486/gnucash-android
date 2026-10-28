#!/bin/sh
JAVA_HOME=/usr/local/openjdk8 PATH=/usr/local/openjdk8/bin:$PATH  ./gradlew assembleProductionRelease --no-daemon "$@" 