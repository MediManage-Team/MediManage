
Add-Type -AssemblyName System.Drawing
$source = "src\main\resources\app_icon.png"
$dest = "src\main\resources\app_icon.ico"

write-host "Converting $source to $dest"

try {
    $img = [System.Drawing.Bitmap]::FromFile($source)
    
    # Create a 64x64 bitmap
    $resized = new-object System.Drawing.Bitmap(64, 64)
    $g = [System.Drawing.Graphics]::FromImage($resized)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.DrawImage($img, 0, 0, 64, 64)
    $g.Dispose()

    $handle = $resized.GetHicon()
    $icon = [System.Drawing.Icon]::FromHandle($handle)
    
    $file = New-Object System.IO.FileStream($dest, "Create")
    $icon.Save($file)
    $file.Close()
    
    $icon.Dispose()
    $resized.Dispose()
    $img.Dispose()
    
    write-host "Success"
}
catch {
    write-error $_
}
