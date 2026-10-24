<#
.SYNOPSIS
  Builds the Mismo Runner release zip for Windows: a jpackage app-image (Mismo Runner.exe plus a
  trimmed Java runtime), README.txt and VERSION.txt, zipped, with its SHA-256.

.DESCRIPTION
  docs/island-runner-implementation-plan.md D7 and Slice 4. Must run on Windows. Run
  "mvn clean package" from the repository root first, so target\mismo-runner.jar is current.

  Output, under mismo-runner\target\release:
    Mismo-Runner-<version>-windows.zip
    Mismo-Runner-<version>-windows.zip.sha256

.PARAMETER JdkHome
  JDK used for jpackage and the bundled runtime. Defaults to $env:JAVA_HOME. JDK 17 or later.

.EXAMPLE
  mvn clean package
  powershell -ExecutionPolicy Bypass -File mismo-runner\package-windows.ps1 -JdkHome "C:\Program Files\Java\jdk-25.0.2"
#>
param(
  [string]$JdkHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'

$module = $PSScriptRoot
$target = Join-Path $module 'target'
$jar = Join-Path $target 'mismo-runner.jar'
if (-not (Test-Path $jar)) {
  throw "$jar not found. Run 'mvn clean package' from the repository root first."
}
if (-not $JdkHome) {
  throw 'Set -JdkHome or JAVA_HOME to a JDK 17+ install.'
}
$jpackage = Join-Path $JdkHome 'bin\jpackage.exe'
if (-not (Test-Path $jpackage)) {
  throw "jpackage not found at $jpackage."
}

# The version comes from the parent pom, the same value the runner reports to the server.
[xml]$pom = Get-Content (Join-Path $module '..\pom.xml')
$version = $pom.project.version
$name = 'Mismo Runner'
$zipName = "Mismo-Runner-$version-windows.zip"

# Modules from "jdeps --print-module-deps", plus what jdeps can't see: java.logging (the log
# file), jdk.crypto.ec (elliptic-curve TLS for https servers), jdk.charsets (console encodings).
$modules = 'java.base,java.compiler,java.desktop,java.logging,java.management,java.naming,java.net.http,' +
  'java.sql,jdk.charsets,jdk.crypto.ec'

$inputDir = Join-Path $target 'jpackage-input'
$images = Join-Path $target 'jpackage'
$release = Join-Path $target 'release'
foreach ($dir in @($inputDir, $images, $release)) {
  if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }
  New-Item -ItemType Directory -Force $dir | Out-Null
}
Copy-Item $jar $inputDir

& $jpackage --type app-image --name $name --app-version $version `
  --vendor 'American Immunization Registry Association' `
  --description 'Runs Mismo optimization locally for Mismo Trainer' `
  --input $inputDir --main-jar 'mismo-runner.jar' --main-class 'org.immregistries.mismo.runner.RunnerMain' `
  --add-modules $modules --win-console --dest $images
if ($LASTEXITCODE -ne 0) { throw "jpackage failed with exit code $LASTEXITCODE" }

$image = Join-Path $images $name
Copy-Item (Join-Path $module 'src\dist\README.txt') $image

$commit = (& git -C $module rev-parse --short HEAD 2>$null)
if (-not $commit) { $commit = 'unknown' }
$dirty = (& git -C $module status --porcelain 2>$null)
if ($dirty) { $commit = "$commit (with uncommitted changes)" }
# jpackage strips java.exe from the bundled runtime; its release file records the version.
$javaVersion = (Select-String -Path (Join-Path $image 'runtime\release') -Pattern '^JAVA_VERSION=' |
  Select-Object -First 1).Line -replace '^JAVA_VERSION=|"', ''
$runtime = "Java $javaVersion (bundled)"
@(
  "Mismo Runner $version",
  "Built:   $((Get-Date).ToString('yyyy-MM-dd HH:mm'))",
  "Commit:  $commit",
  "Runtime: $runtime"
) | Set-Content -Encoding ascii (Join-Path $image 'VERSION.txt')

$zip = Join-Path $release $zipName
# Not Compress-Archive or .NET Framework's ZipFile: under Windows PowerShell 5.1 both write
# backslashes into entry names, which tools other than Explorer warn about. Windows' own tar.exe
# (Windows 10 and later) writes a standard zip.
& tar.exe -a -c -f $zip -C $images $name
if ($LASTEXITCODE -ne 0) { throw "tar failed with exit code $LASTEXITCODE" }
# jpackage leaves the launcher read-only, which makes a later "mvn clean" fail to delete target.
Get-ChildItem -Recurse -File $images | Where-Object IsReadOnly | ForEach-Object { $_.IsReadOnly = $false }

$hash = (Get-FileHash -Algorithm SHA256 $zip).Hash.ToLower()
"$hash  $zipName" | Set-Content -Encoding ascii "$zip.sha256"

$sizeMb = [math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Output "Built $zip ($sizeMb MB)"
Write-Output "SHA-256 $hash"
