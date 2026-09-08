# Libera as portas do projeto quando sobra um processo de uma execução anterior.
#
# O sintoma que isto resolve é o "Port 8080 was already in use" ao subir o
# backend — e a variante silenciosa no frontend, em que o Vite cai para 5174 e
# você fica sem entender por que o endereço mudou.
#
#   .\liberar-portas.ps1
#
# Por segurança, só encerra processos java e node: se houver outra coisa
# ocupando a porta, o script avisa e não mexe.

$portas = @(8080, 5173, 5174, 5175)
$encerrados = 0

foreach ($porta in $portas) {
    $conexoes = Get-NetTCPConnection -LocalPort $porta -State Listen -ErrorAction SilentlyContinue
    if (-not $conexoes) { continue }

    foreach ($conexao in $conexoes) {
        $processo = Get-Process -Id $conexao.OwningProcess -ErrorAction SilentlyContinue
        if (-not $processo) { continue }

        if ($processo.ProcessName -in @('java', 'node')) {
            Write-Host "porta $porta : encerrando $($processo.ProcessName) (PID $($processo.Id))"
            Stop-Process -Id $processo.Id -Force
            $encerrados++
        }
        else {
            Write-Warning "porta $porta : ocupada por '$($processo.ProcessName)' (PID $($processo.Id)) - nao encerrei, veja se e algo seu"
        }
    }
}

Start-Sleep -Seconds 2

# O H2 deixa um arquivo de trava quando o processo morre à força. O banco em si
# fica intacto; só a trava precisa sair para a próxima subida funcionar.
$trava = Join-Path $PSScriptRoot 'backend\data\goianao.lock.db'
if (Test-Path $trava) {
    Remove-Item $trava -Force
    Write-Host 'trava do banco removida (os dados foram preservados)'
}

if ($encerrados -eq 0) {
    Write-Host 'nada a liberar - as portas ja estavam livres'
}
else {
    Write-Host ''
    Write-Host 'pronto. agora:'
    Write-Host '  cd backend  ; mvn spring-boot:run'
    Write-Host '  cd frontend ; npm run dev'
}
