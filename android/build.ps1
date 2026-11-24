# 手工打包 APK（不依赖 Gradle / Android Studio）
#
# 前置：装好 Android SDK 的 build-tools + platform，以及 JDK 17+
#   $env:ANDROID_HOME 指向 SDK 根目录（含 build-tools/、platforms/）
#   $env:JAVA_HOME    指向 JDK
#
# 用法：pwsh -File build.ps1 [versionCode] [versionName]
#   例：pwsh -File build.ps1 21 3.0
#
# 关键点：d8 必须对整个 classes 目录做 dex，否则匿名内部类（MainActivity$1）
# 会缺失，运行时报 NoClassDefFoundError。
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Resolve-Sdk {
    $sdk = $env:ANDROID_HOME
    if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
    if (-not $sdk -and $env:LOCALAPPDATA) {
        $guess = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
        if (Test-Path $guess) { $sdk = $guess }
    }
    if (-not $sdk -or -not (Test-Path $sdk)) {
        throw "找不到 Android SDK，请设置环境变量 ANDROID_HOME"
    }
    return $sdk
}

$SDK = Resolve-Sdk
$JDK = $env:JAVA_HOME
if (-not $JDK -or -not (Test-Path (Join-Path $JDK 'bin\javac.exe'))) {
    throw "找不到 JDK，请设置环境变量 JAVA_HOME（需 JDK 17 以上）"
}

# build-tools 取版本号最大的那个
$BT = Get-ChildItem (Join-Path $SDK 'build-tools') -Directory -ErrorAction Stop |
    Sort-Object { [version]($_.Name -replace '[^0-9.]', '') } -Descending |
    Select-Object -First 1
if (-not $BT) { throw "SDK 里没有 build-tools，请用 sdkmanager 安装" }
$BT = $BT.FullName

# platform 优先用 android-34，没有就取最新的
$platforms = Join-Path $SDK 'platforms'
$PLATFORM = Join-Path $platforms 'android-34\android.jar'
if (-not (Test-Path $PLATFORM)) {
    $latest = Get-ChildItem $platforms -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName 'android.jar') } |
        Sort-Object Name -Descending | Select-Object -First 1
    if (-not $latest) { throw "SDK 里没有 platform，请用 sdkmanager 安装 platforms;android-34" }
    $PLATFORM = Join-Path $latest.FullName 'android.jar'
}

$versionCode = $args.Count -ge 1 ? [int]$args[0] : 1
$versionName = $args.Count -ge 2 ? $args[1] : '1.0'

Write-Host "SDK      $SDK"
Write-Host "build-tools $($BT | Split-Path -Leaf)"
Write-Host "platform $PLATFORM"
Write-Host "jdk      $JDK"

Remove-Item build\classes, build\dex, build\base.apk, build\aligned.apk -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path build\classes, build\dex | Out-Null

# 首次构建自动生成调试签名；已有则复用
$ks = 'build\debug.keystore'
if (-not (Test-Path $ks)) {
    Write-Host "[0/6] 生成调试签名"
    & "$JDK\bin\keytool.exe" -genkeypair -keystore $ks -storepass android -keypass android `
        -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" `
        -keyalg RSA -keysize 2048 -validity 10000 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}

Write-Host "[1/6] aapt2 compile"
& "$BT\aapt2.exe" compile -o build\res.zip --dir res
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "[2/6] aapt2 link (versionCode=$versionCode, versionName=$versionName)"
& "$BT\aapt2.exe" link -o build\base.apk -I $PLATFORM --manifest AndroidManifest.xml `
    -R build\res.zip --min-sdk-version 24 --target-sdk-version 34 `
    --version-code $versionCode --version-name $versionName --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "[3/6] javac"
& "$JDK\bin\javac.exe" --release 8 -nowarn -classpath $PLATFORM -d build\classes `
    src\com\example\deepseekapp\MainActivity.java
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "[4/6] d8 (整个 classes 目录)"
$classFiles = Get-ChildItem build\classes -Recurse -Filter *.class | ForEach-Object { $_.FullName }
Write-Host ("      classes: " + $classFiles.Count)
& "$BT\d8.bat" --lib $PLATFORM --min-api 24 --output build\dex @classFiles
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open((Resolve-Path build\base.apk).Path, 'Update')
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
    $zip, (Resolve-Path build\dex\classes.dex).Path, 'classes.dex', 'Optimal') | Out-Null
$zip.Dispose()

Write-Host "[5/6] zipalign"
& "$BT\zipalign.exe" -f -p 4 build\base.apk build\aligned.apk
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "[6/6] apksigner"
& "$BT\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android `
    --out DeepSeek-demo.apk build\aligned.apk
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

& "$BT\apksigner.bat" verify DeepSeek-demo.apk | Out-Null
Write-Host ("OK: {0:N1} KB, version {1} ({2})" -f ((Get-Item DeepSeek-demo.apk).Length / 1KB), $versionName, $versionCode)