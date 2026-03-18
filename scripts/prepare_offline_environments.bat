@echo off
setlocal

echo ==========================================
echo 0. Preparing Offline Environments
echo ==========================================

:: 1. Prepare Node.js Environment
echo [Node.js] Setting up portable Node.js environment...
set "NODE_DIR=whatsapp-server"
if not exist "%NODE_DIR%\node.exe" (
    echo [Node.js] Downloading standalone node.exe v20...
    curl -# -o "%NODE_DIR%\node.exe" "https://nodejs.org/dist/v20.11.1/win-x64/node.exe"
    if errorlevel 1 (
        echo [ERROR] Failed to download node.exe
        exit /b 1
    )
)

echo [Node.js] Running npm install for local node_modules...
pushd "%NODE_DIR%"
call npm install
if %errorlevel% neq 0 (
    echo [ERROR] npm install failed.
    popd
    exit /b 1
)

:: Ensure Bytenode is installed locally to compile and run the bytecode
call npm install bytenode
if %errorlevel% neq 0 (
    echo [ERROR] npm install bytenode failed.
    popd
    exit /b 1
)
popd

:: 2. Prepare Python Offline Environment
echo [Python] Setting up portable Windows Embeddable Pipeline...
set "PY_SRC=ai_engine\python"
set "PY_ZIP=%TEMP%\python-embed.zip"

if not exist "%PY_SRC%\python.exe" (
    echo [Python] Downloading Python 3.11 Windows Embeddable package...
    mkdir "%PY_SRC%" 2>nul
    curl -# -L -o "%PY_ZIP%" "https://www.python.org/ftp/python/3.11.8/python-3.11.8-embed-amd64.zip"
    if errorlevel 1 (
        echo [ERROR] Failed to download Python.
        exit /b 1
    )
    
    echo [Python] Extracting Python package...
    tar -xf "%PY_ZIP%" -C "%PY_SRC%"
    
    echo [Python] Installing local pip...
    curl -# -o "%PY_SRC%\get-pip.py" "https://bootstrap.pypa.io/get-pip.py"
    
    :: Modify the ._pth file so site-packages works for local pip
    echo Lib\site-packages>> "%PY_SRC%\python311._pth"
    
    "%PY_SRC%\python.exe" "%PY_SRC%\get-pip.py"
    if errorlevel 1 (
        echo [ERROR] Failed to install pip.
        exit /b 1
    )
)

echo [Python] Installing build tools...
"%PY_SRC%\python.exe" -m pip install --upgrade pip setuptools wheel cmake scikit-build-core
if errorlevel 1 (
    echo [ERROR] Failed to install build tools.
    exit /b 1
)

echo [Python] Installing AI dependencies into portable python...
"%PY_SRC%\python.exe" -m pip install -r ai_engine\requirements\requirements.txt
if errorlevel 1 (
    echo [ERROR] pip install requirements failed.
    exit /b 1
)

echo [Python] Installing PyArmor for obfuscation...
"%PY_SRC%\python.exe" -m pip install pyarmor
if errorlevel 1 (
    echo [ERROR] pip install pyarmor failed.
    exit /b 1
)

echo ==========================================
echo Environments prepared successfully.
echo ==========================================
exit /b 0
