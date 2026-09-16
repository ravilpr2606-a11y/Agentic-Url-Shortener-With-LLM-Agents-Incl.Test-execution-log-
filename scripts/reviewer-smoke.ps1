$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"
Write-Host "Health"
Invoke-RestMethod -Method Get -Uri "$base/api/v1/health" | ConvertTo-Json -Depth 10

Write-Host "Workflow creation"
$g = Invoke-RestMethod -Method Post -Uri "$base/api/v1/workflows" -ContentType "application/json" -Body (@{scenario="GREENFIELD"; requirement="Add a reporting capability with API, tests and documentation."} | ConvertTo-Json)
$g | ConvertTo-Json -Depth 10

Write-Host "Inspect"
Invoke-RestMethod -Method Get -Uri "$base/api/v1/workflows/$($g.id)" | ConvertTo-Json -Depth 12

Write-Host "Metrics"
Invoke-RestMethod -Method Get -Uri "$base/api/v1/workflows/metrics" | ConvertTo-Json -Depth 12

Write-Host "Next: approve ARCHITECTURE explicitly, then inspect the workflow. Release approval remains a human gate."
