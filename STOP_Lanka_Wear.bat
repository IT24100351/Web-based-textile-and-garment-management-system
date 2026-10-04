@echo off
echo Stopping processes listening on ports 5173 and 8080...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ports=5173,8080; foreach($p in $ports){ Get-NetTCPConnection -LocalPort $p -ErrorAction SilentlyContinue | ForEach-Object { try { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue } catch {} } }"
echo Done.
pause
