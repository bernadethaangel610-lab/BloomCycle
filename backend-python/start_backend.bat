@echo off
title BloomCycle Backend Server
echo ============================================================
echo   Starting BloomCycle Python Backend and Database
echo ============================================================
echo.
echo Opening server at http://localhost:8000/
echo Web Dashboard: http://localhost:8000/
echo.
"C:\Program Files\Derivative\TouchDesigner\bin\python.exe" "%~dp0server.py" 8000
pause
