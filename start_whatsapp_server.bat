@echo off
setlocal

set "INSTALL_DIR=%~dp0"
set "WA_DIR=%INSTALL_DIR%whatsapp-server"
set "NODE_EXE=%WA_DIR%\node.exe"
set "ENTRY_SCRIPT=%WA_DIR%\start_protected.js"

if not exist "%NODE_EXE%" (
    echo ERROR: Bundled Node.js runtime not found at "%NODE_EXE%".
    pause
    exit /b 1
)

if not exist "%ENTRY_SCRIPT%" (
    echo ERROR: Protected WhatsApp Bridge entrypoint not found at "%ENTRY_SCRIPT%".
    pause
    exit /b 1
)

cd /d "%WA_DIR%"
"%NODE_EXE%" "%ENTRY_SCRIPT%"
pause
