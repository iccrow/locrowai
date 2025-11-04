@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem === Base dir of this .bat ===
set "BASE_DIR=%~dp0"
if "%BASE_DIR:~-1%"=="\" set "BASE_DIR=%BASE_DIR:~0,-1%"

rem === Paths to your bundled DLLs and llama_cpp native lib dir ===
set "CUDA_REDIST=%BASE_DIR%\venv\Lib\site-packages\torch\lib"
set "LLAMA_LIB=%BASE_DIR%\venv\Lib\site-packages\llama_cpp\lib"
set "PYEXE=%BASE_DIR%\venv\Scripts\python.exe"

rem --- Prepend our folders to PATH for this process only ---
set "PATH=%CUDA_REDIST%;%LLAMA_LIB%;%PATH%"

rem --- Forward all arguments to the venv python ---
"%PYEXE%" %*

exit \b %ERRORLEVEL%

endlocal
