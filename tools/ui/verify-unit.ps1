# Run the existing tests with the documented Windows Korean-path workaround.
. (Join-Path $PSScriptRoot 'environment.ps1')
Push-Location $toolingRepo
try {
    & ./gradlew.bat :app:testDebugUnitTest '-Dorg.gradle.jvmargs=-Xmx3072m -Dfile.encoding=COMPAT'
    if ($LASTEXITCODE -ne 0) { throw 'Product unit tests failed.' }
} finally {
    Pop-Location
}
