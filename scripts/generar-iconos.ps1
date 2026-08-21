$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

# Regenera los mipmaps/iconos de Android desde el icono maestro de Lebeche (negro).
$src = 'G:\GITHUB\LOGOS\icono lebeche negro.jpg'
$resBase = 'G:\GITHUB\calendario-lebeche\app\src\main\res'

$icon = New-Object System.Drawing.Bitmap($src)   # 1055x1055, fondo blanco + simbolo negro
$w = $icon.Width; $h = $icon.Height

# 1) Version transparente (blanco -> transparente, negro -> negro) + bbox del simbolo
$trans = New-Object System.Drawing.Bitmap($w, $h)
$minX = $w; $minY = $h; $maxX = -1; $maxY = -1
for ($y = 0; $y -lt $h; $y++) {
    for ($x = 0; $x -lt $w; $x++) {
        $p = $icon.GetPixel($x, $y)
        $luma = [int](($p.R + $p.G + $p.B) / 3)
        $a = 255 - $luma
        if ($a -lt 0) { $a = 0 }
        $trans.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($a, 0, 0, 0))
        if ($luma -lt 128) {
            if ($x -lt $minX) { $minX = $x }
            if ($x -gt $maxX) { $maxX = $x }
            if ($y -lt $minY) { $minY = $y }
            if ($y -gt $maxY) { $maxY = $y }
        }
    }
}

$symW = $maxX - $minX + 1
$symH = $maxY - $minY + 1

# 2) Recortar el simbolo (fondo transparente + simbolo negro)
$symbol = New-Object System.Drawing.Bitmap($symW, $symH)
$g = [System.Drawing.Graphics]::FromImage($symbol)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.DrawImage($trans,
    (New-Object System.Drawing.Rectangle(0, 0, $symW, $symH)),
    (New-Object System.Drawing.Rectangle($minX, $minY, $symW, $symH)),
    [System.Drawing.GraphicsUnit]::Pixel)
$g.Dispose()

function New-SymbolIcon($canvasPx, $fraction, $background) {
    $bmp = New-Object System.Drawing.Bitmap($canvasPx, $canvasPx)
    $g2 = [System.Drawing.Graphics]::FromImage($bmp)
    $g2.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g2.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g2.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    if ($null -ne $background) { $g2.Clear($background) }
    $tw = [int][math]::Floor($canvasPx * $fraction)
    $th = [int][math]::Floor($tw * $symH / $symW)
    $dx = [int][math]::Floor(($canvasPx - $tw) / 2)
    $dy = [int][math]::Floor(($canvasPx - $th) / 2)
    $g2.DrawImage($symbol, $dx, $dy, $tw, $th)
    $g2.Dispose()
    return $bmp
}

function New-RoundIcon($canvasPx, $fraction) {
    $bmp = New-Object System.Drawing.Bitmap($canvasPx, $canvasPx)
    $g2 = [System.Drawing.Graphics]::FromImage($bmp)
    $g2.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g2.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $white = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
    $g2.FillEllipse($white, 0, 0, $canvasPx, $canvasPx)
    $tw = [int][math]::Floor($canvasPx * $fraction)
    $th = [int][math]::Floor($tw * $symH / $symW)
    $dx = [int][math]::Floor(($canvasPx - $tw) / 2)
    $dy = [int][math]::Floor(($canvasPx - $th) / 2)
    $g2.DrawImage($symbol, $dx, $dy, $tw, $th)
    $g2.Dispose(); $white.Dispose()
    return $bmp
}

$densities = @{ 'mdpi' = 1.0; 'hdpi' = 1.5; 'xhdpi' = 2.0; 'xxhdpi' = 3.0; 'xxxhdpi' = 4.0 }

foreach ($k in $densities.Keys) {
    $f = $densities[$k]
    $dir = Join-Path $resBase ("mipmap-$k")
    New-Item -ItemType Directory -Force -Path $dir | Out-Null

    $legacySize = [int](48 * $f)
    $fgSize = [int](108 * $f)

    $legacy = New-SymbolIcon $legacySize 0.66 ([System.Drawing.Color]::White)
    $legacy.Save((Join-Path $dir 'ic_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $legacy.Dispose()

    $round = New-RoundIcon $legacySize 0.66
    $round.Save((Join-Path $dir 'ic_launcher_round.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $round.Dispose()

    $fg = New-SymbolIcon $fgSize 0.66 $null
    $fg.Save((Join-Path $dir 'ic_launcher_foreground.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $fg.Save((Join-Path $dir 'ic_launcher_monochrome.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $fg.Dispose()
}

$icon.Dispose(); $trans.Dispose(); $symbol.Dispose()
Write-Output 'Iconos generados desde LOGOS/icono lebeche negro.jpg'
