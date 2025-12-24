@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-17.0.1
set PATH=%JAVA_HOME%\bin;%PATH%

cd /d "%~dp0"
call mvn clean compile exec:java -Dexec.mainClass="com.foggyframework.benchmark.spider2.generator.Spider2ModelGenerator"
