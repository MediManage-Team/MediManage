@echo off
setlocal

if "%JAVA_HOME%"=="" (
    for /f "delims=" %%D in ('dir /b /ad /o-n "C:\Program Files\Java\jdk-*" 2^>nul') do (
        set "JAVA_HOME=C:\Program Files\Java\%%D"
        goto :java_home_found
    )
)

:java_home_found
if "%JAVA_HOME%"=="" (
    echo ERROR: JAVA_HOME is not set and no JDK was found under C:\Program Files\Java.
    echo Please install JDK 21+ or set JAVA_HOME manually and re-run this script.
    exit /b 1
)

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ERROR: JAVA_HOME is not set to a valid JDK path.
    echo Please set JAVA_HOME to JDK 21+ and re-run this script.
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using JAVA_HOME=%JAVA_HOME%

echo ==========================================
echo 0. Extracting Project Metadata...
echo ==========================================
:: Extract version and artifactId from pom.xml using PowerShell XML parsing.
set "PROJECT_VERSION="
set "ARTIFACT_ID="

echo [Info] Fetching project details from pom.xml...
for /f "usebackq delims=" %%i in (`powershell -NoProfile -Command "$pom=[xml](Get-Content 'pom.xml'); $ns=New-Object System.Xml.XmlNamespaceManager($pom.NameTable); $ns.AddNamespace('m','http://maven.apache.org/POM/4.0.0'); $node=$pom.SelectSingleNode('/m:project/m:artifactId',$ns); if($node){$node.InnerText}"`) do (
    set "ARTIFACT_ID=%%i"
    goto :artifact_id_found
)
:artifact_id_found

for /f "usebackq delims=" %%i in (`powershell -NoProfile -Command "$pom=[xml](Get-Content 'pom.xml'); $ns=New-Object System.Xml.XmlNamespaceManager($pom.NameTable); $ns.AddNamespace('m','http://maven.apache.org/POM/4.0.0'); $node=$pom.SelectSingleNode('/m:project/m:version',$ns); if($node){$node.InnerText}"`) do (
    set "PROJECT_VERSION=%%i"
    goto :version_found
)
:version_found

if "%PROJECT_VERSION%"=="" (
    echo ERROR: Could not extract project version from pom.xml.
    exit /b 1
)
if "%ARTIFACT_ID%"=="" (
    echo ERROR: Could not extract artifact ID from pom.xml.
    exit /b 1
)

echo [Info] Artifact ID: %ARTIFACT_ID%
echo [Info] Project Version: %PROJECT_VERSION%

echo ==========================================
echo 0b. Generating Demo Database...
echo ==========================================
node whatsapp-server/generate-db.js
if %errorlevel% neq 0 (
    echo Database generation failed!
    exit /b %errorlevel%
)

echo ==========================================
echo 1. Cleaning and Building with Maven...
echo ==========================================
call .\mvnw.cmd clean package dependency:copy-dependencies
if %errorlevel% neq 0 (
    echo Maven build failed!
    exit /b %errorlevel%
)

echo ==========================================
echo 2. Preparing Distribution Directory...
echo ==========================================
set "DIST_PATH=dist\MediManage-%PROJECT_VERSION%"
if exist "%DIST_PATH%" rmdir /s /q "%DIST_PATH%"
mkdir "%DIST_PATH%"
mkdir "%DIST_PATH%\lib"

echo Copying Application JAR...
if not exist "target\%ARTIFACT_ID%-%PROJECT_VERSION%.jar" (
    echo ERROR: Expected application jar target\%ARTIFACT_ID%-%PROJECT_VERSION%.jar was not found.
    dir /b target
    exit /b 1
)
copy "target\%ARTIFACT_ID%-%PROJECT_VERSION%.jar" "%DIST_PATH%\MediManage.jar" >nul

echo Copying Dependencies...
copy "target\dependency\*.jar" "%DIST_PATH%\lib\" >nul


echo ==========================================
echo 3. Creating App Image with jpackage...
echo ==========================================
if exist "dist\image" rmdir /s /q "dist\image"

"%JAVA_HOME%\bin\jpackage.exe" ^
  --name "MediManage" ^
  --input "%DIST_PATH%" ^
  --main-jar "MediManage.jar" ^
  --main-class "org.example.MediManage.Launcher" ^
  --type app-image ^
  --dest "dist\image" ^
  --app-version "%PROJECT_VERSION%" ^
  --icon "src\main\resources\app_icon.ico" ^
  --java-options "--add-modules jdk.crypto.ec,jdk.naming.dns,java.naming" ^
  --vendor "MediManage Team" ^
  --copyright "Copyright 2024"

if %errorlevel% neq 0 (
    echo jpackage failed!
    exit /b %errorlevel%
)

echo ==========================================
echo 3b. Preparing Offline ^& Protected Environments...
echo ==========================================
call scripts\prepare_offline_environments.bat
if %errorlevel% neq 0 (
    echo [ERROR] Failed to prepare offline environments.
    exit /b %errorlevel%
)

call scripts\compile_source_code.bat
if %errorlevel% neq 0 (
    echo [ERROR] Failed to compile source code.
    exit /b %errorlevel%
)

echo ==========================================
echo 3c. Bundling Python AI Engine (Protected)...
echo ==========================================
set "AI_DEST=dist\image\MediManage\ai_engine"
mkdir "%AI_DEST%" 2>nul
mkdir "%AI_DEST%\python" 2>nul
mkdir "%AI_DEST%\dist" 2>nul

:: Create exclude file for xcopy
echo __pycache__> "%TEMP%\xcopy_exclude.txt"
echo .pyc>> "%TEMP%\xcopy_exclude.txt"

:: Copy the portable python environment
xcopy "ai_engine\python" "%AI_DEST%\python" /E /I /Y /Q /EXCLUDE:%TEMP%\xcopy_exclude.txt >nul 2>nul

:: Copy ONLY the PyArmor obfuscated dist folder (NOT the raw app/server folders)
xcopy "ai_engine\dist" "%AI_DEST%\dist" /E /I /Y /Q /EXCLUDE:%TEMP%\xcopy_exclude.txt >nul 2>nul

del "%TEMP%\xcopy_exclude.txt" >nul 2>nul
echo AI Engine (Offline ^& Protected) bundled.

echo ==========================================
echo 3d. Bundling WhatsApp Bridge Server (Protected)...
echo ==========================================
set "WA_DEST=dist\image\MediManage\whatsapp-server"
mkdir "%WA_DEST%" 2>nul

:: Copy WhatsApp Server binaries (ONLY the compiled .jsc and node.exe, plus node_modules)
copy "whatsapp-server\node.exe" "%WA_DEST%\" >nul
copy "whatsapp-server\index.jsc" "%WA_DEST%\" >nul
copy "whatsapp-server\security.js" "%WA_DEST%\" >nul
copy "whatsapp-server\start_protected.js" "%WA_DEST%\" >nul
copy "whatsapp-server\.env" "%WA_DEST%\" >nul 2>nul

:: Create exclude file for xcopy WhatsApp
echo __pycache__> "%TEMP%\wa_exclude.txt"
xcopy "whatsapp-server\node_modules" "%WA_DEST%\node_modules" /E /I /Y /Q /EXCLUDE:%TEMP%\wa_exclude.txt >nul 2>nul
del "%TEMP%\wa_exclude.txt" >nul 2>nul

echo WhatsApp server (Offline ^& Protected) bundled.

echo ==========================================
echo 3d. Copying Launcher Scripts...
echo ==========================================
copy "start_ai_engine.bat" "dist\image\MediManage\" >nul
copy "start_whatsapp_server.bat" "dist\image\MediManage\" >nul
echo Launcher scripts copied.

echo ==========================================
echo 4. Compiling Installer with Inno Setup...
echo ==========================================
set "SETUP_EXE=Output\MediManage_Setup_%PROJECT_VERSION%.exe"
if exist "%SETUP_EXE%" del "%SETUP_EXE%"

set "APP_VERSION=%PROJECT_VERSION%"
"C:\Users\ksvik\AppData\Local\Programs\Inno Setup 6\ISCC.exe" /DAppVersion=%PROJECT_VERSION% setup.iss
if %errorlevel% neq 0 (
    echo Inno Setup compilation failed!
    exit /b %errorlevel%
)

echo ==========================================
echo BUILD SUCCESSFUL!
echo Installer is located at: Output\MediManage_Setup_%PROJECT_VERSION%.exe
echo ==========================================
