# Health check (written by Otto-Mate for a Gradle / Android project - adapt it as the project grows).
# Passes only when the app builds AND the tests really ran AND every one passed. Prints each problem once - a compiler
# error with its file and line, a failing test with why and where, or Gradle's own account of what went wrong - then a
# one-line summary. It never trusts results from an earlier run: they're deleted first, and a run that leaves no
# fresh results fails ("the tests didn't run").
#   tools\check.ps1                     everything (the definition of "done", and the gate for a release)
#   tools\check.ps1 -Test name1,name2   only the tests one step is about
param([string[]]$Test = @())
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$problems = @()
$names = @($Test -join ',' -split ',' | Where-Object { $_.Trim() } | ForEach-Object { $_.Trim() })
$filter = @(); foreach ($n in $names) { $filter += '--tests'; $filter += "*$n*" }
$android = Test-Path (Join-Path $root 'app\src\main\AndroidManifest.xml')
$task = if ($android) { 'testDebugUnitTest' } else { 'test' }
$build = if ($android) { 'assembleDebug' } else { 'assemble' }

# results from an earlier run must never count for this one
Get-ChildItem $root -Recurse -Directory -Filter 'test-results' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match '\\build\\test-results$' } |
    ForEach-Object { Remove-Item $_.FullName -Recurse -Force -ErrorAction SilentlyContinue }

# each output line as plain text (Windows PowerShell turns a program's error output into error records, shown with
# "gradlew.bat : " in front - the compiler's "e: ..." lines are on that stream)
$lines = @(& .\gradlew.bat $build $task @filter --console=plain --continue 2>&1 | ForEach-Object { "$_" })
$code = $LASTEXITCODE
foreach ($line in $lines) {
    if ($line -match '(?:^|\s)e: (file:///)?(.+?):(\d+):(\d+):? (.+)$') {
        $file = ($Matches[2] -replace '%20', ' ').Replace($root.Replace('\', '/') + '/', '')
        $problems += "ERROR: $($Matches[5])  (at ${file}:$($Matches[3]))"
    } elseif ($line -match 'AAPT: error: (.+)$' -or $line -match '^ERROR:\s*(.+)$') {
        $problems += "ERROR: $($Matches[1].Trim())"
    }
}
# Gradle's own account of a failure, for the cases the lines above don't name (a broken build script, a test process
# that was killed, a missing SDK)
$wrong = @()
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^\* What went wrong:') {
        for ($j = $i + 1; $j -lt [Math]::Min($i + 8, $lines.Count); $j++) {
            if ($lines[$j] -match '^\* (Try|Exception is):' -or ($lines[$j].Trim() -eq '' -and $wrong.Count)) { break }
            if ($lines[$j].Trim()) { $wrong += $lines[$j].Trim() }
        }
    }
}
$why = if ($wrong) { ($wrong | Select-Object -First 3) -join ' / ' } else { (($lines | Where-Object { $_.Trim() } | Select-Object -Last 3) -join ' / ') }

# this run's test results, each failing test with why and where
$cases = @()
$results = Get-ChildItem $root -Recurse -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match '\\build\\test-results\\' }
foreach ($f in $results) {
    [xml]$x = Get-Content $f.FullName -Raw
    foreach ($tc in $x.SelectNodes('//testcase')) {
        $cases += $tc.name
        $fail = $tc.SelectSingleNode('failure|error')
        if ($fail) {
            $msg = ($fail.GetAttribute('message') -split "`n")[0]
            if ($msg.Length -gt 220) { $msg = $msg.Substring(0, 220) + '...' }
            # where it broke: the first line in the project's own code (not JUnit's, Robolectric's or the JDK's) -
            # the app's line if it's there, else the test's own line
            $frames = $fail.InnerText -split "`n" | Where-Object { $_ -match '\(([\w$]+\.(kt|java)):\d+\)' -and
                $_ -notmatch '\s(at\s+)?(org\.junit|junit\.|org\.robolectric|org\.gradle|org\.mockito|java\.|javax\.|jdk\.|sun\.|kotlin\.|kotlinx\.|androidx\.|android\.|com\.android\.|com\.google\.|dalvik\.)' }
            $at = ($frames | Where-Object { $_ -notmatch 'Test(Kt)?\.' } | Select-Object -First 1)
            if (-not $at) { $at = $frames | Select-Object -First 1 }
            $where = if ($at -match '\(([^)]+\.(kt|java):\d+)\)') { "  (at $($Matches[1]))" } else { '' }
            $problems += "TEST FAILED: $($tc.name) - $msg$where"
        }
    }
}
foreach ($n in $names) {                                   # a test a step is about has to have really run
    if (-not ($cases | Where-Object { $_ -like "*$n*" })) { $problems += "TEST FAILED: $n - it didn't run (no such test yet, or the build failed first)" }
}
if (-not $cases -and -not $problems) { $problems += "ERROR: the tests didn't run - $why" }
if ($code -ne 0 -and -not $problems) { $problems += "ERROR: the build failed (exit $code) - $why" }

$problems = $problems | Sort-Object -Unique
if ($problems.Count) {
    $problems | ForEach-Object { Write-Output $_ }
    Write-Output "CHECK FAILED: $($problems.Count) problem(s)"
    exit 1
}
if ($names) { Write-Output "CHECK PASSED (only: $($names -join ', '); $($cases.Count) test(s) ran) - run tools\check.ps1 with no -Test for everything"; exit 0 }
Write-Output "CHECK PASSED: it builds and all $($cases.Count) tests ran and passed"
exit 0
