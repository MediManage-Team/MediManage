$TargetFile = "$PSScriptRoot\dist\image\MediManage\MediManage.exe"
$ShortcutPath = "$env:APPDATA\Microsoft\Windows\Start Menu\Programs\MediManage.lnk"
$WScriptShell = New-Object -ComObject WScript.Shell
$Shortcut = $WScriptShell.CreateShortcut($ShortcutPath)
$Shortcut.TargetPath = $TargetFile
$Shortcut.WorkingDirectory = "$PSScriptRoot\dist\image\MediManage"
$Shortcut.IconLocation = "$TargetFile,0"
$Shortcut.Save()
Write-Host "Shortcut created at $ShortcutPath"
