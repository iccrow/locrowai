@echo off
setlocal

:: Get the folder this batch file is in
set "SCRIPT_DIR=%~dp0"

:: Go one level up to find pybuild
set "BUILDROOT=%SCRIPT_DIR%..\backend"

echo Removing __pycache__ directories from %BUILDROOT% ...

for /d /r "%BUILDROOT%" %%d in (__pycache__) do (
    echo Deleting: %%d
    rmdir /s /q "%%d"
)

echo Done removing __pycache__ directories.
endlocal
pause
