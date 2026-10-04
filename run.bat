@echo off
setlocal EnableExtensions
cd /d "%~dp0"

if not "%~1"=="" if /I not "%~1"=="--check" (
  echo Usage: run.bat [--check]
  exit /b 2
)

where node >nul 2>nul
if errorlevel 1 (
  echo [TGMS] Error: Node.js 20.19 or newer is required: https://nodejs.org/
  exit /b 1
)

where npm >nul 2>nul
if errorlevel 1 (
  echo [TGMS] Error: npm is required and is normally installed with Node.js.
  exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
  echo [TGMS] Error: Java 17 or newer is required.
  exit /b 1
)



if not exist "backend\.env" (
  if not exist "backend\.env.example" (
    echo [TGMS] Error: Missing backend\.env.example.
    exit /b 1
  )
  if /I "%~1"=="--check" (
    echo [TGMS] Error: backend\.env is missing. Run run.bat once to create it, then configure the database credentials.
    exit /b 1
  )
  copy /Y "backend\.env.example" "backend\.env" >nul
  echo [TGMS] Created backend\.env from backend\.env.example.
  echo [TGMS] Set DB_PASSWORD and a JWT_SECRET of at least 32 bytes, then run this script again.
  exit /b 1
)

findstr /R /C:"^DB_PASSWORD=$" /C:"^DB_PASSWORD=replace_with_local_password$" "backend\.env" >nul
if not errorlevel 1 (
  echo [TGMS] Error: Set DB_PASSWORD in backend\.env before starting TGMS.
  exit /b 1
)

findstr /R /C:"^JWT_SECRET=$" /C:"^JWT_SECRET=replace_with_" "backend\.env" >nul
if not errorlevel 1 (
  echo [TGMS] Error: Set JWT_SECRET in backend\.env to a random value of at least 32 bytes.
  exit /b 1
)

if not exist "node_modules\concurrently" (
  if /I "%~1"=="--check" (
    echo [TGMS] Error: Node.js dependencies are missing. Run npm ci before using --check.
    exit /b 1
  )
  echo [TGMS] Installing dependencies from package-lock.json...
  call npm ci
  if errorlevel 1 exit /b 1
)

node scripts\check-smtp.mjs backend\.env
if errorlevel 1 (
  echo [TGMS] Error: Authentication email cannot be delivered. Correct the SMTP settings in backend\.env.
  exit /b 1
)

if /I "%~1"=="--check" (
  echo [TGMS] Environment check passed.
  exit /b 0
)

echo [TGMS] Starting LankaWear Apparel...
echo [TGMS] Frontend: http://localhost:5173
echo [TGMS] API:      http://localhost:3000/api/health
echo [TGMS] Press Ctrl+C to stop both servers.

call npm run dev
exit /b %ERRORLEVEL%
