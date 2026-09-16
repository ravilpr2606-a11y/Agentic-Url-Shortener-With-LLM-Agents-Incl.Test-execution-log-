$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"
Write-Host "1. Greenfield workflow"
$g = Invoke-RestMethod -Method Post -Uri "$base/api/v1/workflows" -ContentType "application/json" -Body (@{scenario="GREENFIELD"; requirement="Add a reporting capability with API, tests and documentation."} | ConvertTo-Json)
$g | ConvertTo-Json -Depth 8
Write-Host "Approve architecture manually, then inspect: GET /api/v1/workflows/$($g.id)"
Write-Host "2. Ambiguous workflow"
$a = Invoke-RestMethod -Method Post -Uri "$base/api/v1/workflows" -ContentType "application/json" -Body (@{scenario="AMBIGUOUS"; requirement="Make it much faster and change analytics as needed."} | ConvertTo-Json)
$a | ConvertTo-Json -Depth 8
Write-Host "3. Metrics"
Invoke-RestMethod -Method Get -Uri "$base/api/v1/workflows/metrics" | ConvertTo-Json -Depth 8

# After an ambiguous run reaches CLARIFICATION, provide an explicit human decision through /approvals/CLARIFICATION.
