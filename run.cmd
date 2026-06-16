@echo off
REM Wrapper para ejecutar run.ps1 sorteando la política de ejecución para esta invocación
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1"
exit /b %ERRORLEVEL%
