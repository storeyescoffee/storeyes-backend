@echo off
set SERVER_IP=49.13.89.74
set REMOTE_FOLDER=/root/storeyess/backend
set FILE_NAME=storeyes-coffee.jar

echo Building project...
call .\mvnw clean package -DskipTests

echo Uploading files to server...
scp -r target/*.jar root@%SERVER_IP%:%REMOTE_FOLDER%/%FILE_NAME%

echo Restarting backend...
ssh root@%SERVER_IP% "docker restart java-backend"

echo Deployment complete!
