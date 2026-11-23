#!/bin/sh
# Builds (first run only) and starts the console application. Requires Java 17+ and Maven.
cd "$(dirname "$0")"
[ -f target/loan-savings.jar ] || mvn -q package -DskipTests || exit 1
exec java -jar target/loan-savings.jar "$@"
