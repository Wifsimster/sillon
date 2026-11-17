param(
    [Parameter(Mandatory = $true)][string]$Msi,
    [Parameter(Mandatory = $true)][string]$Banner,
    [Parameter(Mandatory = $true)][string]$Dialog
)

$ErrorActionPreference = 'Stop'

$method = [Reflection.BindingFlags]::InvokeMethod
$property = [Reflection.BindingFlags]::GetProperty

function Invoke-Com($object, [string]$name, $kind, $arguments) {
    return $object.GetType().InvokeMember($name, $kind, $null, $object, $arguments)
}

$msiPath = (Resolve-Path $Msi).Path
$images = [ordered]@{
    'WixUI_Bmp_Banner' = (Resolve-Path $Banner).Path
    'WixUI_Bmp_Dialog' = (Resolve-Path $Dialog).Path
}

$installer = New-Object -ComObject WindowsInstaller.Installer

$database = Invoke-Com $installer 'OpenDatabase' $method @($msiPath, 1)
foreach ($name in $images.Keys) {
    $view = Invoke-Com $database 'OpenView' $method @('UPDATE `Binary` SET `Data` = ? WHERE `Name` = ''' + $name + '''')
    $record = Invoke-Com $installer 'CreateRecord' $method @(1)
    Invoke-Com $record 'SetStream' $method @(1, $images[$name]) | Out-Null
    Invoke-Com $view 'Execute' $method @($record) | Out-Null
    Invoke-Com $view 'Close' $method @() | Out-Null
    [Runtime.InteropServices.Marshal]::ReleaseComObject($record) | Out-Null
    [Runtime.InteropServices.Marshal]::ReleaseComObject($view) | Out-Null
}
Invoke-Com $database 'Commit' $method @() | Out-Null
[Runtime.InteropServices.Marshal]::ReleaseComObject($database) | Out-Null
[GC]::Collect()
[GC]::WaitForPendingFinalizers()

$database = Invoke-Com $installer 'OpenDatabase' $method @($msiPath, 0)
foreach ($name in $images.Keys) {
    $view = Invoke-Com $database 'OpenView' $method @('SELECT `Data` FROM `Binary` WHERE `Name` = ''' + $name + '''')
    Invoke-Com $view 'Execute' $method @() | Out-Null
    $row = Invoke-Com $view 'Fetch' $method @()
    if ($null -eq $row) { throw "$name is not in the installer" }
    $stored = Invoke-Com $row 'DataSize' $property @(1)
    $expected = (Get-Item $images[$name]).Length
    if ($stored -ne $expected) { throw "$name holds $stored bytes, expected $expected" }
    Write-Host "$name replaced ($stored bytes)"
    Invoke-Com $view 'Close' $method @() | Out-Null
    [Runtime.InteropServices.Marshal]::ReleaseComObject($row) | Out-Null
    [Runtime.InteropServices.Marshal]::ReleaseComObject($view) | Out-Null
}
[Runtime.InteropServices.Marshal]::ReleaseComObject($database) | Out-Null
[Runtime.InteropServices.Marshal]::ReleaseComObject($installer) | Out-Null
[GC]::Collect()
[GC]::WaitForPendingFinalizers()
