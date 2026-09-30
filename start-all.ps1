# Lance les 7 applications sous Windows (PowerShell), après : .\mvnw.cmd clean package -DskipTests
# Chaque service tourne dans sa propre fenêtre ; fermez-les (ou Ctrl+C) pour arrêter.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Wait-Up($url) {
    for ($i = 0; $i -lt 90; $i++) {
        try { Invoke-WebRequest -UseBasicParsing $url -TimeoutSec 2 | Out-Null; return } catch { Start-Sleep -Seconds 2 }
    }
    Write-Warning "Pas de réponse de $url"
}

function Start-Svc($name) {
    Write-Host "-> $name"
    Start-Process -FilePath "java" -ArgumentList "-Xms64m", "-Xmx256m", "-jar", "$name\target\$name.jar" -WorkingDirectory $PSScriptRoot
}

Start-Svc "eureka-server"; Wait-Up "http://localhost:8761/actuator/health"
Start-Svc "config-server"; Wait-Up "http://localhost:8888/actuator/health"
"class-service", "payment-service", "notification-service", "booking-service" | ForEach-Object { Start-Svc $_ }
8091, 8092, 8093, 8094 | ForEach-Object { Wait-Up "http://localhost:$_/actuator/health" }
Start-Svc "api-gateway"; Wait-Up "http://localhost:8080/actuator/health"
Write-Host "Stack démarrée : gateway http://localhost:8080 - Eureka http://localhost:8761"
