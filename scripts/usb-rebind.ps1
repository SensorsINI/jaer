# Temporary CLI client for USBRebindTester (127.0.0.1:18997).
# Usage: powershell -File scripts/usb-rebind.ps1 status
param(
    [int]$Port = 18997,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Command
)
$ErrorActionPreference = 'Stop'
$line = if ($Command -and $Command.Count -gt 0) { ($Command -join ' ') } else { 'help' }
$client = New-Object System.Net.Sockets.TcpClient
try {
    $client.ReceiveTimeout = 16000
    $client.SendTimeout = 5000
    $client.Connect('127.0.0.1', $Port)
    $stream = $client.GetStream()
    $utf8 = New-Object System.Text.UTF8Encoding $false
    $writer = New-Object System.IO.StreamWriter($stream, $utf8)
    $writer.NewLine = "`n"
    $writer.AutoFlush = $true
    $reader = New-Object System.IO.StreamReader($stream, $utf8)
    $writer.WriteLine($line)
    while ($true) {
        $got = $reader.ReadLine()
        if ($null -eq $got) { break }
        if ($got -eq '.') { break }
        Write-Output $got
    }
} catch {
    Write-Error "USBRebindTester not reachable on 127.0.0.1:$Port (is jAER running?) $_"
    exit 1
} finally {
    if ($client.Connected) { $client.Close() }
}
