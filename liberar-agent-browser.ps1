# Recupera o agent-browser quando ele trava.
#
# O sintoma é a chamada ficar pendurada até estourar, com
# "connection attempt failed ... did not properly respond (os error 10060)".
# Timeout, e não conexão recusada: o cliente encontra um daemon registrado,
# tenta falar com ele e espera uma resposta que não vem.
#
# A causa observada foi um daemon que continua vivo mas parou de escutar em
# qualquer porta. O arquivo de estado ainda aponta para ele, então toda chamada
# nova vai bater nesse processo morto-vivo. Matar o daemon e limpar o estado
# resolve — na próxima chamada tudo é recriado.
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File .\liberar-agent-browser.ps1

$estado = "$env:USERPROFILE\.agent-browser"

Write-Host "Daemons do agent-browser..." -ForegroundColor Cyan
$processos = Get-Process -ErrorAction SilentlyContinue |
    Where-Object { $_.ProcessName -match 'agent-browser' }

if (-not $processos) {
    Write-Host "  nenhum em execucao" -ForegroundColor DarkGray
} else {
    foreach ($p in $processos) {
        $portas = @(Get-NetTCPConnection -OwningProcess $p.Id -State Listen -ErrorAction SilentlyContinue).Count
        $situacao = if ($portas -eq 0) { "ORFAO (nao escuta)" } else { "$portas porta(s)" }
        Write-Host "  PID $($p.Id): $situacao"
        try { Stop-Process -Id $p.Id -Force -ErrorAction Stop; Write-Host "    encerrado" -ForegroundColor Yellow } catch {}
    }
}

Start-Sleep -Seconds 2

# Só os arquivos de sessao. A pasta browsers guarda o Chrome baixado (centenas
# de MB) e nao deve ser tocada.
Write-Host "Estado de sessao..." -ForegroundColor Cyan
$arquivos = Get-ChildItem $estado -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Extension -in '.pid', '.port', '.target', '.config', '.engine', '.stream', '.version' }

if (-not $arquivos) {
    Write-Host "  ja estava limpo" -ForegroundColor DarkGray
} else {
    foreach ($a in $arquivos) {
        Remove-Item $a.FullName -Force -ErrorAction SilentlyContinue
        Write-Host "  removido $($a.Name)" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "Pronto. A proxima chamada recria o daemon do zero." -ForegroundColor Green
Write-Host "Para conferir:  agent-browser doctor" -ForegroundColor DarkGray
