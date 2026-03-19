@echo off
echo =============================================
echo   MediManage AI Engine - Cloud Backend Launcher
echo =============================================
echo.

set VENV_DIR=ai_engine\.venv

:: Create venv if needed
if not exist "%VENV_DIR%\Scripts\python.exe" (
    echo [1/3] Creating Python virtual environment...
    python -m venv "%VENV_DIR%"
    if errorlevel 1 (
        echo ERROR: Failed to create venv. Ensure Python 3.8+ is installed.
        echo Download from: https://www.python.org/downloads/
        pause
        exit /b 1
    )
    echo Virtual environment created.
) else (
    echo [1/3] Virtual environment found.
)

:: Install/update dependencies
echo [2/3] Installing cloud AI dependencies...
"%VENV_DIR%\Scripts\pip.exe" install -r ai_engine\requirements\requirements.txt
if errorlevel 1 (
    echo WARNING: Some packages may have failed to install.
)

:: Start server
echo [3/3] Starting AI Engine Server...
echo.
"%VENV_DIR%\Scripts\python.exe" ai_engine\server\server.py
pause
