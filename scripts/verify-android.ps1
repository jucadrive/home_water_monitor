param(
    [ValidateSet('Build', 'Unit', 'Full', 'Connected', 'EndToEnd')]
    [string]$Mode = 'Full',
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$DeviceSerial,
    [string]$ServerOrigin,
    [string]$MqttDeviceId
)

$ErrorActionPreference = 'Stop'
$androidProject = Join-Path $PSScriptRoot '../mobile/android'
if (!$AndroidSdk) { $AndroidSdk = Join-Path $PSScriptRoot '../.tools/android-sdk' }
if (!$JavaHome -or !(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
    throw 'Set JAVA_HOME or -JavaHome to JDK 21 (Android). Backend verification uses JDK 25 separately.'
}
if (!(Test-Path -LiteralPath (Join-Path $AndroidSdk 'platforms/android-36/android.jar'))) {
    throw 'Install Android SDK API 36 and build-tools 35.0.0. See docs/ANDROID_SETUP.md.'
}
$savedJavaHome = $env:JAVA_HOME
$savedAndroidHome = $env:ANDROID_HOME
$savedSerial = $env:ANDROID_SERIAL
$verificationExitCode = 1
try {
    $env:JAVA_HOME = $JavaHome
    $env:ANDROID_HOME = (Resolve-Path -LiteralPath $AndroidSdk).Path
    $arguments = switch ($Mode) {
        'Build' { @('assembleDebug', 'assembleRelease') }
        'Unit' { @('testDebugUnitTest') }
        'Full' { @('testDebugUnitTest', 'lintDebug', 'assembleDebug', 'assembleRelease') }
        'Connected' { @('connectedDebugAndroidTest', '-Pandroid.testInstrumentationRunnerArguments.class=com.juca.hydroponic.FormsTest') }
        'EndToEnd' {
            if (!$ServerOrigin -or !$MqttDeviceId) { throw 'EndToEnd requires an isolated -ServerOrigin and -MqttDeviceId.' }
            $originUri = [Uri]$ServerOrigin
            if ($originUri.Host -ne '127.0.0.1' -or $originUri.Scheme -ne 'http' -or $originUri.UserInfo -or $originUri.Query -or $originUri.Fragment) {
                throw 'The acceptance server must use explicit HTTP loopback without credentials.'
            }
            @('connectedDebugAndroidTest', '-Pandroid.testInstrumentationRunnerArguments.class=com.juca.hydroponic.FormsTest,com.juca.hydroponic.AppFlowTest',
                "-PacceptanceOrigin=$ServerOrigin", "-Pandroid.testInstrumentationRunnerArguments.serverOrigin=$ServerOrigin",
                "-Pandroid.testInstrumentationRunnerArguments.deviceId=$MqttDeviceId")
        }
    }
    if ($Mode -in @('Connected', 'EndToEnd')) {
        if (!$DeviceSerial) { throw 'Explicit -DeviceSerial is required. Do not select a personal device implicitly.' }
        $env:ANDROID_SERIAL = $DeviceSerial
        & (Join-Path $AndroidSdk 'platform-tools/adb.exe') -s $DeviceSerial get-state
        if ($LASTEXITCODE -ne 0) { throw 'Selected Android test device is not available.' }
    }
    Push-Location $androidProject
    try {
        & ./gradlew.bat @arguments --no-daemon --console=plain
        $verificationExitCode = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    $env:JAVA_HOME = $savedJavaHome
    $env:ANDROID_HOME = $savedAndroidHome
    $env:ANDROID_SERIAL = $savedSerial
}
exit $verificationExitCode
