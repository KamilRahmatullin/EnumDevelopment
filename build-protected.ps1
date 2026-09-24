$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
Set-Location -LiteralPath $PSScriptRoot

function Assert-CommandExists {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command was not found in PATH: $Name"
    }
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command,

        [Parameter(Mandatory = $false)]
        [string[]]$Arguments = @()
    )

    Write-Host ("> {0} {1}" -f $Command, ($Arguments -join " ")) -ForegroundColor DarkGray
    & $Command @Arguments
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw ("Command failed with exit code {0}: {1} {2}" -f $exitCode, $Command, ($Arguments -join " "))
    }
}

Assert-CommandExists -Name "mvn"
Assert-CommandExists -Name "javac"
Assert-CommandExists -Name "java"

$artifact = "EnumDevelopment"
$version = "1.0.6"
$asmVersion = "9.7.1"
$dependencyPluginVersion = "3.6.1"

Invoke-Checked -Command "mvn" -Arguments @("clean", "package")

$target = Join-Path $PSScriptRoot "target"
$tools = Join-Path $target "protector-tools"
$libs = Join-Path $target "protector-libs"
New-Item -ItemType Directory -Force -Path $tools, $libs | Out-Null

$libsForMaven = $libs.Replace("\", "/")
foreach ($asmArtifact in @("asm", "asm-commons", "asm-tree")) {
    Invoke-Checked -Command "mvn" -Arguments @(
        ("org.apache.maven.plugins:maven-dependency-plugin:{0}:copy" -f $dependencyPluginVersion),
        ("-Dartifact=org.ow2.asm:{0}:{1}" -f $asmArtifact, $asmVersion),
        "-DoutputDirectory=$libsForMaven",
        "-Dmdep.stripVersion=true"
    )
}

$asmJars = @(Get-ChildItem -LiteralPath $libs -Filter "*.jar" | Sort-Object Name | ForEach-Object { $_.FullName })
if ($asmJars.Count -lt 3) {
    throw "ASM libraries were not downloaded to: $libs"
}

$asmClasspath = [string]::Join([IO.Path]::PathSeparator, [string[]]$asmJars)
Invoke-Checked -Command "javac" -Arguments @(
    "--release", "8",
    "-encoding", "UTF-8",
    "-cp", $asmClasspath,
    "-d", $tools,
    "tools\PayloadJarObfuscator.java",
    "tools\PayloadProtector.java"
)

$toolEntries = @($tools) + $asmJars
$toolClasspath = [string]::Join([IO.Path]::PathSeparator, [string[]]$toolEntries)

$clean = Join-Path $target "$artifact-$version.jar"
$obfuscated = Join-Path $target "$artifact-$version-obfuscated.jar"
$mapping = Join-Path $target "$artifact-$version-mapping.txt"
$protected = Join-Path $target "$artifact-$version-protected.jar"

if (-not (Test-Path -LiteralPath $clean)) {
    throw "Maven did not create the source JAR: $clean"
}

Invoke-Checked -Command "java" -Arguments @(
    "-cp", $toolClasspath,
    "PayloadJarObfuscator",
    $clean,
    $obfuscated,
    $mapping,
    "com/enumdev/enumdevelopment",
    "com/enumdev/enumdevelopment/Main",
    "com/enumdev/enumdevelopment/internal/RuntimeEntrypoint",
    $version
)

Invoke-Checked -Command "java" -Arguments @(
    "-cp", $toolClasspath,
    "PayloadProtector",
    $clean,
    $obfuscated,
    $protected,
    "com/enumdev/enumdevelopment/Main",
    "com/enumdev/enumdevelopment/internal/RuntimeEntrypoint",
    "META-INF/enum.payload",
    "4544565031303621",
    "EnumDevelopment|1.0.6|payload",
    "82adc2598b202314ccf3b39bbacdc243",
    "3ce9cf3bd4b83c0444050c6309f1118a",
    "402826a54e1651b0d5313693575737f5",
    "77a0b5e3c0712fbc2b256c8380dfb805",
    "45445650"
)

if (-not (Test-Path -LiteralPath $protected)) {
    throw "Protected JAR was not created: $protected"
}
if (-not (Test-Path -LiteralPath $mapping)) {
    throw "Mapping file was not created: $mapping"
}

Write-Host ""
Write-Host "Protected JAR: $protected" -ForegroundColor Green
Write-Host "Mapping:       $mapping" -ForegroundColor Yellow
