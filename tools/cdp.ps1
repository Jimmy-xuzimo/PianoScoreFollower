param(
    [Parameter(Mandatory = $true)][string]$Expression,
    [int]$Port = 9222
)

$ErrorActionPreference = 'Stop'

$targets = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/json"
$target = $targets | Where-Object { $_.type -eq 'page' } | Select-Object -First 1
if (-not $target) { throw 'no page target' }

$ws = [System.Net.WebSockets.ClientWebSocket]::new()
$uri = [Uri]$target.webSocketDebuggerUrl
$ws.ConnectAsync($uri, [System.Threading.CancellationToken]::None).Wait()

$payload = @{
    id     = 1
    method = 'Runtime.evaluate'
    params = @{
        expression    = $Expression
        returnByValue = $true
        awaitPromise  = $true
    }
} | ConvertTo-Json -Depth 6 -Compress

$bytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
$seg = [ArraySegment[byte]]::new($bytes)
$ws.SendAsync($seg, [System.Net.WebSockets.WebSocketMessageType]::Text, $true,
    [System.Threading.CancellationToken]::None).Wait()

$buffer = New-Object byte[] 262144
$sb = [System.Text.StringBuilder]::new()
do {
    $recvSeg = [ArraySegment[byte]]::new($buffer)
    $result = $ws.ReceiveAsync($recvSeg, [System.Threading.CancellationToken]::None).Result
    [void]$sb.Append([System.Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count))
} while (-not $result.EndOfMessage)

$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, 'bye',
    [System.Threading.CancellationToken]::None).Wait()

$response = $sb.ToString() | ConvertFrom-Json
if ($response.result.exceptionDetails) {
    Write-Output ('EXCEPTION: ' + ($response.result.exceptionDetails | ConvertTo-Json -Depth 6 -Compress))
}
Write-Output ($response.result.result.value | ConvertTo-Json -Depth 8)