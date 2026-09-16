$ErrorActionPreference = "Stop"
Write-Host "=== Agentic URL Shortener Verification ==="
Write-Host "Run from a clean checkout with Docker/PostgreSQL available."
java -version
mvn -version
Write-Host "Declared JUnit @Test methods in source (excluding @TestInstance):"
$tests = (Get-ChildItem -Recurse -Filter *.java src/test/java | Select-String -Pattern '^\s*@Test\s*$').Count
Write-Host "  $tests"
if ($tests -ne 47) { throw "Expected 47 executable @Test methods in the submitted baseline; found $tests" }

mvn clean test
mvn verify

Write-Host "=== Reviewer evidence references ==="
Write-Host "docs/assessment/executed-evidence.md"
Write-Host "docs/assessment/final-engineering-summary.md"
Write-Host "docs/assessment/reviewer-navigation.md"
Write-Host "Then execute scripts/demo.ps1 and capture fresh outputs when a reviewer requires current runtime evidence."
