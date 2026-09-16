# live-model-capture.ps1
#
# I wrote this to close the one gap my automated suite cannot cover: proving
# the LLM-backed agent path works against a real model, not just its
# deterministic fallback. It also captures the /api/v1/workflows/metrics
# response body, which was my other outstanding evidence gap.
#
# What this proves, in order:
#   1. A vague GREENFIELD requirement is routed to the human CLARIFICATION
#      gate because the *model* flagged ambiguity -- the deterministic path
#      would not have flagged it, since the scenario is not AMBIGUOUS. This
#      is the single clearest demonstration that model reasoning is actually
#      driving a governance decision.
#   2. A crisp GREENFIELD requirement is NOT flagged, so the model is
#      discriminating rather than flagging everything.
#   3. Both artifacts carry "source":"llm" provenance rather than
#      "source":"deterministic-fallback".
#   4. The live metrics endpoint response shape.
#
# Prerequisites:
#   - $env:ANTHROPIC_API_KEY set
#   - docker compose up -d   (the app needs the compose Postgres; note the
#     test suite does NOT -- it uses Testcontainers)
#
# Usage:
#   .\scripts\live-model-capture.ps1
#
# Output: docs/assessment/live-model-capture-output.txt

$ErrorActionPreference = "Stop"

$OutFile = "docs/assessment/live-model-capture-output.txt"
$BaseUrl = "http://localhost:8080"

function Write-Capture {
    param([string]$Text)
    Write-Host $Text
    Add-Content -Path $OutFile -Value $Text
}

# --- Preflight ------------------------------------------------------------

if (-not $env:ANTHROPIC_API_KEY) {
    Write-Error "ANTHROPIC_API_KEY is not set. I need a real key for this capture to mean anything."
    exit 1
}

New-Item -ItemType Directory -Force -Path "docs/assessment" | Out-Null
Set-Content -Path $OutFile -Value "=== Live LLM-backed agent capture ==="
Write-Capture "Captured at: $(Get-Date -Format o)"
Write-Capture "Model: $(if ($env:LLM_MODEL) { $env:LLM_MODEL } else { 'claude-3-5-haiku-20241022 (default)' })"
Write-Capture "API key present: yes (value not logged)"
Write-Capture ""

# --- Start the application with the LLM path enabled ----------------------

$env:LLM_ENABLED = "true"

Write-Capture "--- Starting application with LLM_ENABLED=true ---"
$app = Start-Process -FilePath "mvn" `
    -ArgumentList "spring-boot:run" `
    -NoNewWindow -PassThru `
    -RedirectStandardOutput "target/live-model-app.log" `
    -RedirectStandardError "target/live-model-app.err.log"

try {
    # Wait for health, up to 120s.
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        try {
            $health = Invoke-RestMethod -Uri "$BaseUrl/api/v1/health" -TimeoutSec 5
            $ready = $true
            Write-Capture "Health check passed after ~$($i * 2)s: $($health | ConvertTo-Json -Compress)"
            break
        } catch {
            # not up yet
        }
    }

    if (-not $ready) {
        Write-Capture "ERROR: application did not become healthy. See target/live-model-app.log"
        exit 1
    }
    Write-Capture ""

    # --- Case 1: vague requirement, GREENFIELD scenario -------------------
    #
    # The scenario is deliberately NOT "AMBIGUOUS". So if this run ends up
    # at the CLARIFICATION gate, the only thing that could have put it there
    # is the model's own ambiguityDetected verdict.

    Write-Capture "--- Case 1: vague requirement submitted as GREENFIELD ---"
    $vagueBody = @{
        requirement = "Make the analytics better and faster somehow, and clean up whatever needs cleaning up"
        scenario    = "GREENFIELD"
    } | ConvertTo-Json

    $vague = Invoke-RestMethod -Uri "$BaseUrl/api/v1/workflows" `
        -Method Post -ContentType "application/json" -Body $vagueBody

    Write-Capture "Requirement: (vague, GREENFIELD)"
    Write-Capture "Resulting status:      $($vague.status)"
    Write-Capture "Resulting currentNode: $($vague.currentNode)"
    Write-Capture ""

    $vagueDetail = Invoke-RestMethod -Uri "$BaseUrl/api/v1/workflows/$($vague.id)"
    $vagueAnalysis = $vagueDetail.artifacts | Where-Object { $_.type -eq "AMBIGUITY_ANALYSIS" } | Select-Object -Last 1

    Write-Capture "AMBIGUITY_ANALYSIS artifact content:"
    Write-Capture $vagueAnalysis.content
    Write-Capture ""

    if ($vagueAnalysis.content -match '"source"\s*:\s*"llm"') {
        Write-Capture "PROVENANCE: source=llm  -> the live model produced this, not the fallback."
    } else {
        Write-Capture "PROVENANCE: source is NOT llm -> the fallback ran. The live path did NOT execute."
        Write-Capture "            Check target/live-model-app.log for an LLM warning line."
    }

    if ($vague.currentNode -eq "CLARIFICATION") {
        Write-Capture "GOVERNANCE: model-detected ambiguity routed a GREENFIELD run to the human gate."
    } else {
        Write-Capture "GOVERNANCE: the model did not flag this requirement as ambiguous."
    }
    Write-Capture ""

    # --- Case 2: crisp requirement, GREENFIELD scenario -------------------
    #
    # Control case. If this one ALSO gets flagged, the model is just
    # flagging everything and the signal is worthless.

    Write-Capture "--- Case 2 (control): crisp requirement submitted as GREENFIELD ---"
    $crispBody = @{
        requirement = "Add a GET /api/v1/urls/{code}/analytics endpoint returning total click count and last-clicked timestamp, with p95 latency under 250ms"
        scenario    = "GREENFIELD"
    } | ConvertTo-Json

    $crisp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/workflows" `
        -Method Post -ContentType "application/json" -Body $crispBody

    Write-Capture "Resulting status:      $($crisp.status)"
    Write-Capture "Resulting currentNode: $($crisp.currentNode)"

    $crispDetail = Invoke-RestMethod -Uri "$BaseUrl/api/v1/workflows/$($crisp.id)"
    $crispAnalysis = $crispDetail.artifacts | Where-Object { $_.type -eq "AMBIGUITY_ANALYSIS" } | Select-Object -Last 1
    Write-Capture "AMBIGUITY_ANALYSIS artifact content:"
    Write-Capture $crispAnalysis.content
    Write-Capture ""

    if ($crisp.currentNode -eq "ARCHITECTURE_APPROVAL") {
        Write-Capture "DISCRIMINATION: crisp requirement proceeded to the architecture gate, so"
        Write-Capture "                the model is discriminating rather than flagging everything."
    }
    Write-Capture ""

    # --- Case 3: architecture agent on the live path ----------------------

    Write-Capture "--- Case 3: ARCHITECTURE artifact provenance ---"
    $arch = $crispDetail.artifacts | Where-Object { $_.type -eq "ARCHITECTURE" } | Select-Object -Last 1
    if ($arch) {
        Write-Capture $arch.content
    } else {
        Write-Capture "(no ARCHITECTURE artifact yet for this run)"
    }
    Write-Capture ""

    # --- Case 4: metrics endpoint response shape --------------------------

    Write-Capture "--- Case 4: GET /api/v1/workflows/metrics (live response body) ---"
    $metrics = Invoke-RestMethod -Uri "$BaseUrl/api/v1/workflows/metrics"
    Write-Capture ($metrics | ConvertTo-Json -Depth 5)
    Write-Capture ""

    # --- LLM warning lines from the app log -------------------------------

    Write-Capture "--- Any LLM warning lines from the application log ---"
    $warnings = Select-String -Path "target/live-model-app.log" -Pattern "LLM call|LLM integration|Could not parse LLM" -ErrorAction SilentlyContinue
    if ($warnings) {
        $warnings | ForEach-Object { Write-Capture $_.Line }
    } else {
        Write-Capture "(none -- no LLM failures or fallbacks were logged)"
    }

} finally {
    Write-Capture ""
    Write-Capture "--- Stopping application ---"
    if ($app -and -not $app.HasExited) {
        Stop-Process -Id $app.Id -Force -ErrorAction SilentlyContinue
    }
    Write-Capture "Capture written to $OutFile"
}
