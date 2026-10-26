Add-Type -AssemblyName System.Drawing
$img = [System.Drawing.Image]::FromFile((Resolve-Path "releases/v1.2/kids_back_verified.png").Path)
Write-Output "Size: $($img.Width) x $($img.Height)"
$img.Dispose()
