@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-25"
set "PATH=%JAVA_HOME%\bin;%PATH%"

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
