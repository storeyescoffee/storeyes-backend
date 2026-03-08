@echo off
set SERVER_IP=49.13.89.74
set REMOTE_FOLDER=/root/storeyess/backend
set ZIP_FILE=source-code.zip

echo Compressing source files...
powershell -NoProfile -Command "$ErrorActionPreference='Stop'; try { $zip = [System.IO.Compression.ZipFile]::Open('%ZIP_FILE%', [System.IO.Compression.ZipArchiveMode]::Create); $files = @('src', 'pom.xml', 'mvnw', 'mvnw.cmd', '.mvn'); foreach ($file in $files) { if (Test-Path $file) { if ((Get-Item $file) -is [System.IO.DirectoryInfo]) { Get-ChildItem -Path $file -Recurse | ForEach-Object { $entryName = $_.FullName.Replace((Get-Location).Path + '\', '').Replace('\', '/'); [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, $entryName, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null } } else { $entryName = $file.Replace('\', '/'); [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Resolve-Path $file).Path, $entryName, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null } } }; $zip.Dispose(); Write-Host 'Zip created successfully' } catch { Write-Host 'Using alternative compression method...'; Compress-Archive -Path src, pom.xml, mvnw, mvnw.cmd, .mvn -DestinationPath '%ZIP_FILE%' -Force }"

echo Uploading zip file to server...
scp %ZIP_FILE% root@%SERVER_IP%:%REMOTE_FOLDER%/%ZIP_FILE%

echo Running build script on server...
ssh root@%SERVER_IP% "cd %REMOTE_FOLDER% && sudo ./build-server.sh"

echo Cleaning up local zip file...
del %ZIP_FILE%

echo Deployment complete!

