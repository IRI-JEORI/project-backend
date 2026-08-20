$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($env:AWS_S3_ENABLED)) {
    $env:AWS_S3_ENABLED = 'true'
}
if ([string]::IsNullOrWhiteSpace($env:AWS_REGION)) {
    $env:AWS_REGION = 'ap-northeast-2'
}
if ([string]::IsNullOrWhiteSpace($env:AWS_S3_BUCKET)) {
    $env:AWS_S3_BUCKET = 'nunnun-wake-storage-608841098309-ap-northeast-2-an'
}

if ([string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY)) {
    Write-Error 'OPENAI_API_KEY is required.'
    exit 1
}

if ($null -eq (Get-Command aws -ErrorAction SilentlyContinue)) {
    Write-Error 'AWS authentication is required. Install the AWS CLI, run aws login, and retry.'
    exit 1
}

& aws sts get-caller-identity --output json *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Error 'AWS authentication is required. Run aws login and retry.'
    exit 1
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repositoryRoot
try {
    & .\gradlew.bat bootRun --console=plain
    $gradleExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

exit $gradleExitCode
