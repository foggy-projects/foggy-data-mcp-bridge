@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-17.0.1"
set "PATH=%JAVA_HOME%\bin;%PATH%"
java -version
mvn test -pl foggy-fsscript -Dtest=FsscriptScriptEngineTest 2>&1
