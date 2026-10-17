# Run a Nookworks Gradle task with a Java 25 JDK. From the mod root:
#   .\tools\gradle.ps1 runGameTestServer
#   .\tools\gradle.ps1 compileJava
# The JDK is JAVA_HOME when that is a Java 25, else the newest Temurin 25 under Program Files\Eclipse Adoptium.
# The exit code is Gradle's own, whatever Gradle writes to stderr on the way (redirected with *> or not).
# A running Minecraft client locks the build; close it with WM_CLOSE first (see CLAUDE.md).
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$modRoot = Split-Path -Parent $PSScriptRoot

function JavaVersionOf([string]$dir) {
    $release = Join-Path $dir "release"
    if (-not (Test-Path $release)) { return $null }
    $line = Select-String -LiteralPath $release -Pattern '^JAVA_VERSION="([0-9]+)' | Select-Object -First 1
    if ($line) { [int]$line.Matches[0].Groups[1].Value } else { $null }
}

$jdk = $null
if ($env:JAVA_HOME -and (JavaVersionOf $env:JAVA_HOME) -eq 25) {
    $jdk = $env:JAVA_HOME
} else {
    $adoptium = Join-Path $env:ProgramFiles "Eclipse Adoptium"
    $jdk = Get-ChildItem -LiteralPath $adoptium -Directory -Filter "jdk-25*" -ErrorAction SilentlyContinue |
        Where-Object { (JavaVersionOf $_.FullName) -eq 25 } |
        Sort-Object { [version](($_.Name -replace '^jdk-', '' -replace '-hotspot$', '') -replace '[^0-9.]', '') } -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $jdk) {
    Write-Host "No Java 25 JDK found: set JAVA_HOME to one, or install Temurin 25 (https://adoptium.net)."
    exit 1
}
$env:JAVA_HOME = $jdk
if (-not $GradleArgs -or $GradleArgs.Count -eq 0) {
    $GradleArgs = @("build")
}
Set-Location $modRoot
[Environment]::CurrentDirectory = $modRoot
# PowerShell 5.1 turns each line Gradle writes to stderr into an error record once the output is redirected; with a
# stop-on-error preference the first warning ended the run as a failure. They go on as plain lines, and the exit code
# is Gradle's.
$ErrorActionPreference = "Continue"
& (Join-Path $modRoot "gradlew.bat") @GradleArgs 2>&1 | ForEach-Object { "$_" }
exit $LASTEXITCODE
