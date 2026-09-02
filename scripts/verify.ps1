param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

Write-Host "1/4 Checking health..."
$health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health"
if ($health.status -ne "UP") { throw "Backend is not healthy." }

Write-Host "2/4 Calling Qwen..."
$chat = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/llm/chat" -ContentType "application/json" -Body '{"prompt":"Reply with only: Qwen connection verified."}'
if ([string]::IsNullOrWhiteSpace($chat.content)) { throw "Qwen returned an empty response." }

Write-Host "3/4 Writing and searching a vector document..."
$document = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/vectors/documents" -ContentType "application/json" -Body '{"document":"Retrieval augmentation grounds generated claims in source evidence.","metadata":{"source":"verification","type":"demo"}}'
if ([string]::IsNullOrWhiteSpace($document.id)) { throw "Chroma did not return a document ID." }
$search = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/vectors/search" -ContentType "application/json" -Body '{"query":"How does retrieval support factual claims?","limit":1}'

Write-Host "4/4 Checking safe API logs..."
$logs = Invoke-RestMethod -Uri "$BaseUrl/api/llm/logs"
if ($logs.Count -lt 1) { throw "No API logs were recorded." }

Write-Host "Verification complete. Capture the Qwen output, vector result, and API logs for your evidence." -ForegroundColor Green
