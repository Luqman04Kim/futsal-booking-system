$filePath = "d:\luqma\TEST\FYP3\src\main\resources\templates\reports.html"
$lines = Get-Content $filePath -Encoding UTF8
$newLines = @()
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($i -lt 697 -or $i -ge 764) {
        $newLines += $lines[$i]
    }
}
Set-Content -Path $filePath -Value $newLines -Encoding UTF8
