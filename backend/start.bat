@echo off
cd /d %~dp0

for /f "usebackq tokens=1* delims==" %%a in ("..\.env") do (
  if not "%%a"=="" if not "%%a"=="#" (
    for /f "delims=#" %%v in ("%%b") do set "%%a=%%v"
  )
)

java -Xmx128m -XX:+UseSerialGC -jar target\fanet-platform-0.1.0-SNAPSHOT.jar
