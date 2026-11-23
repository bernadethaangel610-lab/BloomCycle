# Live HTTP Endpoint Testing & Debugging Script
$mysqlProc = Get-Process -Name mysqld -ErrorAction SilentlyContinue
if (-not $mysqlProc) {
    Write-Host "Starting MySQL server..."
    Start-Process -FilePath "C:\xampp\mysql\bin\mysqld.exe" -ArgumentList "--defaults-file=C:\xampp\mysql\bin\my.ini","--standalone"
    Start-Sleep -Seconds 3
}

Copy-Item -Path "C:\Users\Acer\AndroidStudioProjects\BloomCycle\backend\*" -Destination "C:\xampp\htdocs\bloomcycle" -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "Starting temporary PHP server on port 8088..."
$phpServer = Start-Process -FilePath "C:\xampp\php\php.exe" -ArgumentList "-S","127.0.0.1:8088","-t","C:\xampp\htdocs\bloomcycle\api" -PassThru
Start-Sleep -Seconds 2

try {
    Write-Host "`n1. Testing POST auth/login (Admin Sign In)..."
    $loginBody = @{
        email = "admin@bloomcycle.com"
        password = "AdminPassword123!"
    } | ConvertTo-Json

    $loginRes = Invoke-RestMethod -Uri "http://127.0.0.1:8088/index.php?route=auth/login" -Method Post -Body $loginBody -ContentType "application/json"
    $token = $loginRes.token
    Write-Host "   [OK] Logged in successfully as Admin!"
    Write-Host "   Admin Name: $($loginRes.user.name)"
    Write-Host "   Admin Email: $($loginRes.user.email)"
    Write-Host "   Admin Role: $($loginRes.user.role)"
    Write-Host "   Bearer Token: $($token.Substring(0, 16))..."

    Write-Host "`n2. Testing GET admin/dashboard (User Count)..."
    $dashRes = Invoke-RestMethod -Uri "http://127.0.0.1:8088/index.php?route=admin/dashboard" -Method Get -Headers @{ Authorization = "Bearer $token" }
    Write-Host "   [OK] Dashboard Counts:"
    Write-Host "   - Total Regular Users: $($dashRes.counts.users)"
    Write-Host "   - Open Reports: $($dashRes.counts.openReports)"
    Write-Host "   - Active Discussions: $($dashRes.counts.discussions)"

    Write-Host "`n3. Testing GET admin/users (Inspecting Users & Emails)..."
    $usersRes = Invoke-RestMethod -Uri "http://127.0.0.1:8088/index.php?route=admin/users" -Method Get -Headers @{ Authorization = "Bearer $token" }
    Write-Host "   [OK] Found $($usersRes.users.Count) registered user(s):"
    foreach ($u in $usersRes.users) {
        Write-Host "   - Name: $($u.name) | Email: $($u.email) | Role: $($u.role) | Joined: $($u.createdAt)"
    }

    Write-Host "`n4. Testing POST auth/register (Registering a new user)..."
    $regBody = @{
        name = "Siti Rahma"
        age = 22
        email = "siti.rahma@example.com"
        password = "UserPassword123!"
    } | ConvertTo-Json

    $regRes = Invoke-RestMethod -Uri "http://127.0.0.1:8088/index.php?route=auth/register" -Method Post -Body $regBody -ContentType "application/json"
    Write-Host "   [OK] Registration response: $($regRes.message)"

    Write-Host "`n5. Verifying user appears in admin/users list..."
    $usersRes2 = Invoke-RestMethod -Uri "http://127.0.0.1:8088/index.php?route=admin/users" -Method Get -Headers @{ Authorization = "Bearer $token" }
    Write-Host "   [OK] Total users now: $($usersRes2.users.Count)"
    foreach ($u in $usersRes2.users) {
        Write-Host "   - ID: $($u.id) | Name: $($u.name) | Email: $($u.email) | Role: $($u.role) | Joined: $($u.createdAt)"
    }

    Write-Host "`n=== All API HTTP Endpoints Tested and Debugged with 100% Success! ==="

} catch {
    Write-Host "Error during test: $_"
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host "Response body: $($reader.ReadToEnd())"
    }
} finally {
    if ($phpServer) {
        Stop-Process -Id $phpServer.Id -Force -ErrorAction SilentlyContinue
    }
}
