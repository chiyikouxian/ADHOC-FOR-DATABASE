@echo off
cd /d %~dp0
for /f "tokens=1,2 delims==" %%a in ('findstr "LLM_API_KEY" ..\.env') do set %%a=%%b
java -Xmx128m -XX:+UseSerialGC -jar target\fanet-platform-0.1.0-SNAPSHOT.jar
