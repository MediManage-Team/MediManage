@echo off
setlocal

cd /d "%~dp0"

if exist "dist\image\MediManage\MediManage.exe" (
    echo Running packaged Windows app image...
    if "%MEDIMANAGE_LAUNCHER_DRY_RUN%"=="1" (
        echo DRY RUN: dist\image\MediManage\MediManage.exe
        exit /b 0
    )
    "dist\image\MediManage\MediManage.exe"
    exit /b %errorlevel%
)

if exist "C:\Program Files\MediManage\MediManage.exe" (
    echo Running installed Windows app...
    if "%MEDIMANAGE_LAUNCHER_DRY_RUN%"=="1" (
        echo DRY RUN: C:\Program Files\MediManage\MediManage.exe
        exit /b 0
    )
    "C:\Program Files\MediManage\MediManage.exe"
    exit /b %errorlevel%
)

if exist "mvnw.cmd" (
    echo Running development app via Maven...
    if "%MEDIMANAGE_LAUNCHER_DRY_RUN%"=="1" (
        echo DRY RUN: .\mvnw.cmd javafx:run
        exit /b 0
    )
    call ".\mvnw.cmd" javafx:run
    exit /b %errorlevel%
)

echo ERROR: MediManage launcher not found.
echo Build the Windows image with build_full_installer.bat or install the app first.
exit /b 1
