$ErrorActionPreference = 'Stop'
# Run after Maven has resolved the dependencies. Uses the model bundled with Tess4J.
$taskModelDirectory = Join-Path $PSScriptRoot 'tessdata'
$taskJar = Join-Path $env:USERPROFILE '.m2\repository\net\sourceforge\tess4j\tess4j\5.20.0\tess4j-5.20.0.jar'
if (-not (Test-Path -LiteralPath $taskJar)) {
    throw 'Resolve the backend dependencies first: mvn -f backend/pom.xml dependency:resolve'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
New-Item -ItemType Directory -Path $taskModelDirectory -Force | Out-Null
$taskArchive = [System.IO.Compression.ZipFile]::OpenRead($taskJar)
try {
    $taskEntry = $taskArchive.GetEntry('tessdata/eng.traineddata')
    if ($null -eq $taskEntry) { throw 'The Tess4J English language model was not found.' }
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile(
        $taskEntry, (Join-Path $taskModelDirectory 'eng.traineddata'), $true)
} finally { $taskArchive.Dispose() }
Write-Output "English OCR model ready in $taskModelDirectory"
