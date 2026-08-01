param(
    [string]$ChatBaseUrl = "https://api.deepseek.com",
    [string]$EmbeddingBaseUrl = "http://127.0.0.1:11434",
    [string]$ChatModel = "deepseek-v4-flash",
    [string]$EmbeddingModel = "qwen3-embedding:0.6b",
    [string]$OllamaModels = "D:\.ollama\models"
)

$ErrorActionPreference = "Stop"

function Invoke-JsonPost {
    param(
        [string]$Uri,
        [object]$Body
    )
    Invoke-RestMethod `
        -Method Post `
        -Uri $Uri `
        -ContentType "application/json" `
        -Body ($Body | ConvertTo-Json -Depth 12 -Compress) `
        -TimeoutSec 120
}

function Resolve-DeepSeekApiKey {
    if (-not [string]::IsNullOrWhiteSpace($env:DEEPSEEK_API_KEY)) {
        return $env:DEEPSEEK_API_KEY.Trim()
    }
    foreach ($path in @(".env", "..\.env")) {
        if (-not (Test-Path $path)) {
            continue
        }
        foreach ($line in Get-Content $path) {
            if ($line -match "^\s*DEEPSEEK_API_KEY\s*=\s*(.+?)\s*$") {
                return $Matches[1].Trim()
            }
        }
    }
    return ""
}

Write-Host "== Ollama tags =="
if ([string]::IsNullOrWhiteSpace($env:OLLAMA_MODELS)) {
    $env:OLLAMA_MODELS = $OllamaModels
}
$tags = Invoke-RestMethod -Method Get -Uri "$EmbeddingBaseUrl/api/tags" -TimeoutSec 10
$tags.models | Select-Object name, size, modified_at | Format-Table

Write-Host "== Chat completion =="
$apiKey = Resolve-DeepSeekApiKey
if ([string]::IsNullOrWhiteSpace($apiKey)) {
    throw "DEEPSEEK_API_KEY is not set in environment or .env."
}
$chatBody = @{
    model = $ChatModel
    stream = $false
    messages = @(
        @{ role = "system"; content = "You are a local customer-service validation assistant for an index12306 project." },
        @{ role = "user"; content = "Reply in one short sentence: local chat model is connected." }
    )
}
$chatHeaders = @{ Authorization = "Bearer $apiKey" }
$chat = Invoke-RestMethod `
    -Method Post `
    -Uri "$ChatBaseUrl/v1/chat/completions" `
    -Headers $chatHeaders `
    -ContentType "application/json" `
    -Body ($chatBody | ConvertTo-Json -Depth 12 -Compress) `
    -TimeoutSec 120
$chat.choices[0].message.content

Write-Host "== Embedding =="
$embeddingBody = @{
    model = $EmbeddingModel
    input = "ticket refund id verification"
}
$embedding = Invoke-JsonPost -Uri "$EmbeddingBaseUrl/v1/embeddings" -Body $embeddingBody
$vector = $embedding.data[0].embedding
"embedding_dimension=$($vector.Count)"
