$action = {
    $proc = $Event.SourceEventArgs.NewEvent.TargetInstance
    if ($proc.Name -eq "cmd.exe" -or $proc.Name -eq "conhost.exe") {
        $logLine = "$($proc.ProcessId) | $($proc.Name) | $($proc.CommandLine) | $(Get-Date)"
        Add-Content "C:\Users\ksvik\IdeaProjects\MediManage\proc_log.txt" $logLine
    }
}
Register-CimIndicationEvent -ClassName Win32_ProcessStartTrace -SourceIdentifier "MonitorProcessStart" -Action $action

Write-Host "Monitoring started. Waiting for 10 seconds..."
Start-Sleep -Seconds 10

Unregister-Event -SourceIdentifier "MonitorProcessStart"
Write-Host "Monitoring finished."
