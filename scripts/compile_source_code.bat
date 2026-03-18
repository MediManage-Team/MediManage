@echo off
setlocal

echo ==========================================
echo 0b. Protecting Source Code
echo ==========================================

:: 1. Compile Node.js with Bytenode
echo [Node.js] Compiling index.js into V8 Bytecode (index.jsc)...
pushd "whatsapp-server"

:: Ensure we use the local node.exe and local bytenode
node.exe .\node_modules\bytenode\lib\cli.js -c index.js

if not exist index.jsc (
    echo [ERROR] Bytenode compilation failed.
    popd
    exit /b 1
)

:: Create a tiny entry script that loads the bytecode instead of the raw JS
echo require('bytenode'); require('./index.jsc'); > start_protected.js

popd
echo [Node.js] Compilation successful.

:: 2. Obfuscate Python with PyArmor
echo [Python] Obfuscating AI Engine with PyArmor...
set "PY_SRC=ai_engine\python"

:: Clean previous builds
if exist "ai_engine\dist" rmdir /s /q "ai_engine\dist"

:: We need to obfuscate the app module and the server.py entry point
:: -O specifies the output directory
:: We use the pyarmor.exe installed in the portable python Scripts directory
"%PY_SRC%\Scripts\pyarmor.exe" gen -O ai_engine\dist ai_engine\server\server.py ai_engine\app -r

if not exist "ai_engine\dist\server.py" (
    echo [ERROR] PyArmor obfuscation failed.
    exit /b 1
)

:: Recreate the server folder structure in dist so it matches what the launcher expects
mkdir "ai_engine\dist\server" 2>nul
move "ai_engine\dist\server.py" "ai_engine\dist\server\server.py" >nul

:: Copy requirement txts and config json to the obfuscated dist folder so it has everything
mkdir "ai_engine\dist\requirements" 2>nul
copy "ai_engine\requirements\*.txt" "ai_engine\dist\requirements\" >nul 2>nul
copy "ai_engine\mcp_config.json" "ai_engine\dist\" >nul 2>nul
copy "ai_engine\server\mcp_server.py" "ai_engine\dist\server\" >nul 2>nul

echo [Python] Obfuscation successful.

echo ==========================================
echo Source Code Protected.
echo ==========================================
exit /b 0
