$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
Set-Location $PSScriptRoot

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command,

        [Parameter(Mandatory = $false)]
        [string[]]$Arguments = @()
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw ("Команда завершилась с кодом {0}: {1} {2}" -f $LASTEXITCODE, $Command, ($Arguments -join ' '))
    }
}

$artifact = "EnumDevelopment"
$version = "1.0.5"
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

$asmJars = @(Get-ChildItem -Path $libs -Filter "*.jar" | Sort-Object Name | ForEach-Object { $_.FullName })
if ($asmJars.Count -lt 3) {
    throw "Не удалось загрузить библиотеки ASM в $libs"
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
    throw "Maven не создал исходный JAR: $clean"
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
    "4544565031303521",
    "EnumDevelopment|1.0.5|payload",
    "82adc2598b202314ccf3b39bbacdc243",
    "3ce9cf3bd4b83c0444050c6309f1118a",
    "402826a54e1651b0d5313693575737f5",
    "77a0b5e3c0712fbc2b256c8380dfb805",
    "45445650"
)

if (-not (Test-Path -LiteralPath $protected)) {
    throw "Защищённый JAR не был создан: $protected"
}
if (-not (Test-Path -LiteralPath $mapping)) {
    throw "Mapping-файл не был создан: $mapping"
}

Write-Host ""
Write-Host "Готовый защищённый JAR: $protected" -ForegroundColor Green
Write-Host "Mapping: $mapping" -ForegroundColor Yellow
