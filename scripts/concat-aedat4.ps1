# Concatenate a jAER VCR deck folder to one AEDAT-4. Does not delete sources.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
if (Test-Path (Join-Path $Root "build\classes")) {
    $Cp = "$(Join-Path $Root 'build\classes');$(Join-Path $Root 'lib\*');$(Join-Path $Root 'jars\*')"
} elseif (Test-Path (Join-Path $Root "jAER.jar")) {
    $Cp = "$(Join-Path $Root 'jAER.jar');$(Join-Path $Root 'lib\*');$(Join-Path $Root 'jars\*')"
} else {
    Write-Error "jAER classes not found. Run ant compile, or use an installed jAER.jar."
}
& java -cp $Cp net.sf.jaer.eventio.aedat4.Aedat4Concat @args
exit $LASTEXITCODE
