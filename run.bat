@echo off
rem Builds (first run only) and starts the console application. Requires Java 17+ and Maven.
cd /d "%~dp0"
if not exist target\loan-savings.jar call mvn -q package -DskipTests
java -jar target\loan-savings.jar %*
