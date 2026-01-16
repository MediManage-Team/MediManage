@echo off
echo Adding WiX to PATH...
set "PATH=%PATH%;C:\Program Files (x86)\WiX Toolset v3.14\bin"

echo Cleaning previous build...
rmdir /s /q "dist\installer"

echo Running jpackage...
"C:\Program Files\Java\jdk-25\bin\jpackage.exe" --name "MediManage" --input "dist\MediManage-0.1.5" --main-jar "MediManage.jar" --main-class "org.example.MediManage.Launcher" --type exe --win-menu --win-shortcut --dest "dist\installer"

if %errorlevel% neq 0 (
    echo jpackage failed with error code %errorlevel%
    exit /b %errorlevel%
)

echo Done! Installer created in dist/installer
