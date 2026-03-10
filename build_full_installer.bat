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
echo 1. Cleaning and Building with Maven...
echo ==========================================
call mvn clean package dependency:copy-dependencies
if %errorlevel% neq 0 (
    echo Maven build failed!
    exit /b %errorlevel%
)

echo ==========================================
echo 2. Preparing Distribution Directory...
echo ==========================================
if exist "dist\MediManage-0.1.5" rmdir /s /q "dist\MediManage-0.1.5"
mkdir "dist\MediManage-0.1.5"
mkdir "dist\MediManage-0.1.5\lib"

echo Copying Application JAR...
copy "target\Project_File-0.1.5.jar" "dist\MediManage-0.1.5\MediManage.jar" >nul

echo Copying Dependencies...
copy "target\dependency\*.jar" "dist\MediManage-0.1.5\lib\" >nul


echo ==========================================
echo 3. Creating App Image with jpackage...
echo ==========================================
if exist "dist\image" rmdir /s /q "dist\image"

"%JAVA_HOME%\bin\jpackage.exe" ^
  --name "MediManage" ^
  --input "dist\MediManage-0.1.5" ^
  --main-jar "MediManage.jar" ^
  --main-class "org.example.MediManage.Launcher" ^
  --type app-image ^
  --dest "dist\image" ^
  --app-version "0.1.5" ^
  --icon "src\main\resources\app_icon.ico" ^
  --vendor "MediManage Team" ^
  --copyright "Copyright 2024"

if %errorlevel% neq 0 (
    echo jpackage failed!
    exit /b %errorlevel%
)

echo ==========================================
echo 4. Compiling Installer with Inno Setup...
echo ==========================================
if exist "dist\MediManage_Setup_0.1.5.exe" del "dist\MediManage_Setup_0.1.5.exe"

"C:\Users\ksvik\AppData\Local\Programs\Inno Setup 6\ISCC.exe" setup.iss
if %errorlevel% neq 0 (
    echo Inno Setup compilation failed!
    exit /b %errorlevel%
)

echo ==========================================
echo BUILD SUCCESSFUL!
echo Installer is located at: Output\MediManage_Setup_0.1.5.exe
echo ==========================================
