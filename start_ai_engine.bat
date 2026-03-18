@echo off
setlocal

:: ==========================================
:: MediManage AI Engine Server Launcher (Offline/Protected)
:: ==========================================

:: Get the directory where this script is located (the install root)
set "INSTALL_DIR=%~dp0"
set "PYTHON_EXE=%INSTALL_DIR%ai_engine\python\python.exe"

:: PyArmor outputs the encrypted scripts into the "dist" directory
set "PYARMOR_SERVER=%INSTALL_DIR%ai_engine\dist\server\server.py"

echo Starting MediManage AI Engine Server...
echo.

:: Launch the portable python.exe running the encrypted text file
"%PYTHON_EXE%" "%PYARMOR_SERVER%"

echo.
pause
