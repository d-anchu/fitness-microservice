Write-Host "╔═══════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║          FITNESS MICROSERVICE - START ALL SERVICES            ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

$projectRoot = "c:\Users\dhruv\Projects\fitness-microservice - kiro"
cd $projectRoot

# Check prerequisites
Write-Host "Checking prerequisites..." -ForegroundColor Yellow

# Check Java
try {
    $javaVersion = java -version 2>&1 | Select-String "version" | Select-Object -First 1
    Write-Host "  ✓ Java: $javaVersion" -ForegroundColor Green
} catch {
    Write-Host "  ✗ Java not found" -ForegroundColor Red
    exit 1
}

# Check MongoDB
try {
    $mongoStatus = mongosh --quiet --eval "db.version()" 2>$null
    Write-Host "  ✓ MongoDB: $mongoStatus" -ForegroundColor Green
} catch {
    Write-Host "  ✗ MongoDB not running" -ForegroundColor Red
    exit 1
}

# Check RabbitMQ
$rabbitConnection = Get-NetTCPConnection -LocalPort 5672 -ErrorAction SilentlyContinue
if ($rabbitConnection) {
    Write-Host "  ✓ RabbitMQ running on port 5672" -ForegroundColor Green
} else {
    Write-Host "  ✗ RabbitMQ not running on port 5672" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Starting services in order..." -ForegroundColor Yellow
Write-Host ""

# Service startup order
$startupSequence = @(
    @{
        Name = "Eureka Server"
        Path = "eurekaserver"
        Jar = "eurekaserver\target\eurekaserver-0.0.1-SNAPSHOT.jar"
        Port = 8761
        WaitTime = 15
        LogFile = "eureka-server.log"
    },
    @{
        Name = "Config Server"
        Path = "configserverc\configserverc"
        Jar = "configserverc\configserverc\target\configserverc-0.0.1-SNAPSHOT.jar"
        Port = 8888
        WaitTime = 10
        LogFile = "config-server.log"
    },
    @{
        Name = "User Service"
        Path = "userservice"
        Jar = "userservice\target\userservice-0.0.1-SNAPSHOT.jar"
        Port = 8081
        WaitTime = 10
        LogFile = "user-service.log"
    },
    @{
        Name = "Activity Service"
        Path = "activityservice"
        Jar = "activityservice\target\activityservice-0.0.1-SNAPSHOT.jar"
        Port = 8082
        WaitTime = 10
        LogFile = "activity-service.log"
    },
    @{
        Name = "AI Service"
        Path = "aiservice"
        Jar = "aiservice\target\aiservice-0.0.1-SNAPSHOT.jar"
        Port = 8083
        WaitTime = 10
        LogFile = "ai-service.log"
    },
    @{
        Name = "API Gateway"
        Path = "gateway\gateway"
        Jar = "gateway\gateway\target\gateway-0.0.1-SNAPSHOT.jar"
        Port = 8080
        WaitTime = 10
        LogFile = "gateway.log"
    }
)

foreach ($service in $startupSequence) {
    Write-Host "Starting $($service.Name)..." -ForegroundColor Cyan
    
    # Check if already running
    $existingProcess = Get-NetTCPConnection -LocalPort $service.Port -ErrorAction SilentlyContinue
    if ($existingProcess) {
        Write-Host "  ⚠ Already running on port $($service.Port)" -ForegroundColor Yellow
    } else {
        # Check if JAR exists
        if (Test-Path $service.Jar) {
            $logPath = Join-Path $projectRoot $service.LogFile
            Start-Process java -ArgumentList "-jar", $service.Jar `
                -WorkingDirectory (Join-Path $projectRoot $service.Path) `
                -WindowStyle Hidden `
                -RedirectStandardOutput $logPath `
                -RedirectStandardError "$logPath.err"
            
            Write-Host "  ✓ Started. Waiting $($service.WaitTime)s for initialization..." -ForegroundColor Green
            Start-Sleep -Seconds $service.WaitTime
            
            # Verify it started
            $running = Get-NetTCPConnection -LocalPort $service.Port -ErrorAction SilentlyContinue
            if ($running) {
                Write-Host "  ✓ $($service.Name) is now running on port $($service.Port)" -ForegroundColor Green
            } else {
                Write-Host "  ✗ $($service.Name) failed to start. Check $($service.LogFile)" -ForegroundColor Red
            }
        } else {
            Write-Host "  ✗ JAR not found: $($service.Jar)" -ForegroundColor Red
            Write-Host "    Run: cd $($service.Path) ; .\mvnw.cmd clean package -DskipTests" -ForegroundColor Yellow
        }
    }
    Write-Host ""
}

# Start Frontend
Write-Host "Starting React Frontend..." -ForegroundColor Cyan
$frontendRunning = Get-NetTCPConnection -LocalPort 5173 -ErrorAction SilentlyContinue
if ($frontendRunning) {
    Write-Host "  ⚠ Frontend already running on port 5173" -ForegroundColor Yellow
} else {
    cd "$projectRoot\fitness-app-frontend"
    if (Test-Path "package.json") {
        Start-Process npm -ArgumentList "run", "dev" -WindowStyle Hidden
        Write-Host "  ✓ Frontend started" -ForegroundColor Green
        Start-Sleep -Seconds 5
    } else {
        Write-Host "  ✗ Frontend package.json not found" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "╔═══════════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║                    ALL SERVICES STARTED                        ║" -ForegroundColor Green
Write-Host "╚═══════════════════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""
Write-Host "Service URLs:" -ForegroundColor Yellow
Write-Host "  • Eureka Dashboard: http://localhost:8761" -ForegroundColor White
Write-Host "  • API Gateway: http://localhost:8080" -ForegroundColor White
Write-Host "  • Frontend: http://localhost:5173" -ForegroundColor White
Write-Host "  • RabbitMQ Management: http://localhost:15672 (guest/guest)" -ForegroundColor White
Write-Host ""
Write-Host "Next: Run .\VERIFY-SYSTEM.ps1 to test the complete flow" -ForegroundColor Cyan
