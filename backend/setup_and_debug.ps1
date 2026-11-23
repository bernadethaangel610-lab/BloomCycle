# BloomCycle Database Deployment and Debugging Script

Write-Host "=========================================================="
Write-Host "BloomCycle: Deploying Backend to MySQL Database & Debugging"
Write-Host "=========================================================="

# 1. Start MySQL if not running
$mysqlProc = Get-Process -Name mysqld -ErrorAction SilentlyContinue
if (-not $mysqlProc) {
    Write-Host "[1/6] Starting MySQL server..."
    Start-Process -FilePath "C:\xampp\mysql\bin\mysqld.exe" -ArgumentList "--defaults-file=C:\xampp\mysql\bin\my.ini","--standalone"
    Start-Sleep -Seconds 3
} else {
    Write-Host "[1/6] MySQL server is already running (PID: $($mysqlProc.Id))."
}

# 2. Create Database
Write-Host "[2/6] Ensuring 'bloomcycle' database exists..."
& "C:\xampp\mysql\bin\mysql.exe" -u root -e "CREATE DATABASE IF NOT EXISTS bloomcycle CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 3. Import Schema
Write-Host "[3/6] Importing schema.sql into 'bloomcycle'..."
Get-Content "C:\Users\Acer\AndroidStudioProjects\BloomCycle\backend\schema.sql" | & "C:\xampp\mysql\bin\mysql.exe" -u root bloomcycle

# 4. Create local .env configuration
Write-Host "[4/6] Writing .env configuration..."
$envText = @"
BLOOM_DB_HOST=127.0.0.1
BLOOM_DB_NAME=bloomcycle
BLOOM_DB_USER=root
BLOOM_DB_PASSWORD=
"@
Set-Content -Path "C:\Users\Acer\AndroidStudioProjects\BloomCycle\backend\.env" -Value $envText -Encoding UTF8

# 5. Create Administrator Account
Write-Host "[5/6] Ensuring Administrator account is created..."
& "C:\xampp\php\php.exe" "C:\Users\Acer\AndroidStudioProjects\BloomCycle\backend\bin\create-admin.php" "Admin BloomCycle" "admin@bloomcycle.com" "AdminPassword123!"

# 6. Deploy to C:\xampp\htdocs\bloomcycle for Apache web serving
Write-Host "[6/6] Copying backend to C:\xampp\htdocs\bloomcycle..."
$dest = "C:\xampp\htdocs\bloomcycle"
if (-not (Test-Path $dest)) {
    New-Item -ItemType Directory -Path $dest -Force | Out-Null
}
Copy-Item -Path "C:\Users\Acer\AndroidStudioProjects\BloomCycle\backend\*" -Destination $dest -Recurse -Force

Write-Host "`nRunning Backend Diagnostics and Testing..."
& "C:\xampp\php\php.exe" "C:\Users\Acer\AndroidStudioProjects\BloomCycle\backend\test_api.php"

Write-Host "`nDeployment to Database and Backend Debugging Complete!"
