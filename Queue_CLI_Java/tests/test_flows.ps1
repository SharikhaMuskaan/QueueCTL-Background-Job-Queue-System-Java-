# tests/test_flows.ps1
# Windows PowerShell automated smoke test for queuectl
# Usage: Open an elevated PowerShell in project root and run: .\tests\test_flows.ps1

$jar = "target\queuectl-1.0-SNAPSHOT.jar"
$db = "queue.db"

Write-Host "Cleaning DB..."
if (Test-Path $db) { Remove-Item $db -Force }

Write-Host "Enqueue success job..."
& java -jar $jar enqueue '{"id":"t_ok","command":"echo OK","max_retries":2}'

Write-Host "Enqueue failing job..."
& java -jar $jar enqueue '{"id":"t_bad","command":"nonexistent_cmd_abc","max_retries":2}'

Write-Host "Starting worker in a new background process (will run ~12s)..."
# Start a new PowerShell process to run the worker in foreground so logs are visible in the new window
$startInfo = @{
    FilePath = "powershell"
    ArgumentList = "-NoExit","-Command","java -jar $jar worker start 1"
    WindowStyle = "Normal"
}
$proc = Start-Process @startInfo -PassThru
Write-Host "Worker PID: $($proc.Id)"
Write-Host "Letting worker run 12 seconds to process attempts..."
Start-Sleep -Seconds 12

Write-Host "Stopping worker process (force) PID $($proc.Id)..."
# Attempt a graceful stop is not available for remote process; force stop
Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

Write-Host "`n=== DLQ ==="
& java -jar $jar dlq list

Write-Host "`n=== Completed ==="
& java -jar $jar list completed

Write-Host "`n=== Pending ==="
& java -jar $jar list pending

Write-Host "`nTest script finished."
