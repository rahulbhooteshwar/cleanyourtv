# Builds my-tv\theme-glass\wallpapers from the repo's own theme pictures (their credits stay in those theme folders),
# so Launcher settings > Wallpaper on the TV can pick any of them. The folder is gitignored; run this, then push:
#   powershell -ExecutionPolicy Bypass -File my-tv\make-wallpapers.ps1
#   powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost 172.20.10.9 my-tv\theme-glass
# 00-glass.jpg sorts first, so it stays the picture Home shows until another one is picked on the TV.
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$themes = Join-Path $repo 'themes'
$dst = Join-Path $PSScriptRoot 'theme-glass\wallpapers'
if (Test-Path $dst) { Remove-Item -Recurse -Force $dst }
New-Item -ItemType Directory -Force $dst | Out-Null

Copy-Item (Join-Path $themes 'glass\wallpaper.jpg') (Join-Path $dst '00-glass.jpg')
foreach ($t in 'aurora', 'cinematic', 'ruska', 'red-rain') {
    Copy-Item (Join-Path $themes "$t\wallpaper.jpg") (Join-Path $dst "10-$t.jpg")
}
# Collections, with a prefix so the picker groups them. Seasonal (halloween, christmas, dussehra) and blocky are left out.
$sets = [ordered]@{ 'scenes' = '20-scenes'; 'nature-calm' = '30-nature'; 'sweden' = '40-sweden'; 'storm' = '50-storm' }
foreach ($t in $sets.Keys) {
    Get-ChildItem (Join-Path $themes "$t\wallpapers") -File | Where-Object { $_.Extension -match '^\.(jpg|jpeg|png|webp)$' } |
        ForEach-Object { Copy-Item $_.FullName (Join-Path $dst "$($sets[$t])-$($_.Name)") }
}
$files = Get-ChildItem $dst
Write-Host "$($files.Count) wallpapers, $([math]::Round(($files | Measure-Object Length -Sum).Sum / 1MB, 1)) MB in $dst"
