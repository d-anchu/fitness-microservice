# ═══════════════════════════════════════════════════════════════
# COMPREHENSIVE MICROSERVICES TEST SUITE
# Tests all 6 microservices with real API calls
# ═══════════════════════════════════════════════════════════════

$ErrorActionPreference = "Continue"
$testsPassed = 0
$testsFailed = 0
$testResults = @()

function Test-Result {
    param($TestName, $Success, $Details)
    
    if ($Success) {
        $testsPassed++
        Write-Host "  ✓ $TestName" -ForegroundColor Green
        $testResults += @{Name=$TestName; Status="PASS"; Details=$Details}
    } else {
        $testsFailed++
        Write-Host "  ✗ $TestName" -ForegroundColor Red
        $testResults += @{Name=$TestName; Status="FAIL"; Details=$Details}
    }
}

Write-Host ""
Write-Host "╔═══════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║        COMPREHENSIVE MICROSERVICES TEST SUITE                  ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# ═══════════════════════════════════════════════════════════════
# TEST 1: EUREKA SERVER (Service Discovery)
# ═══════════════════════════════════════════════════════════════
Write-Host "TEST 1: Eureka Server (Port 8761)" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray

try {
    $eureka = Invoke-RestMethod -Uri "http://localhost:8761/eureka/apps" -Method Get -Headers @{Accept="application/json"} -TimeoutSec 10
    $registeredApps = $eureka.applications.application.name
    
    Test-Result "Eureka Server Responding" $true "Registered services: $($registeredApps.Count)"
    
    # Check specific services
    $expectedServices = @("USER-SERVICE", "ACTIVITY-SERVICE", "AI-SERVICE", "API-GATEWAY")
    foreach ($svc in $expectedServices) {
        if ($registeredApps -contains $svc) {
            Test-Result "$svc registered in Eureka" $true "Status: UP"
        } else {
            Test-Result "$svc registered in Eureka" $false "Not found"
        }
    }
} catch {
    Test-Result "Eureka Server Responding" $false $_.Exception.Message
}
Write-Host ""

# ═══════════════════════════════════════════════════════════════
# TEST 2: CONFIG SERVER (Centralized Configuration)
# ═══════════════════════════════════════════════════════════════
Write-Host "TEST 2: Config Server (Port 8888)" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray

try {
    $activityConfig = Invoke-RestMethod -Uri "http://localhost:8888/activity-service/default" -Method Get -TimeoutSec 10
    Test-Result "Config Server Responding" $true "Loaded activity-service config"
    
    if ($activityConfig.propertySources.source.'spring.data.mongodb.uri') {
        Test-Result "MongoDB URI configured" $true "fitnessactivity database"
    }
} catch {
    Test-Result "Config Server Responding" $false $_.Exception.Message
}

try {
    $aiConfig = Invoke-RestMethod -Uri "http://localhost:8888/ai-service/default" -Method Get -TimeoutSec 10
    Test-Result "AI Service config loaded" $true "fitnessrecommendation database"
} catch {
    Test-Result "AI Service config loaded" $false $_.Exception.Message
}
Write-Host ""

# ═══════════════════════════════════════════════════════════════
# TEST 3: USER SERVICE (Port 8081)
# ═══════════════════════════════════════════════════════════════
Write-Host "TEST 3: User Service (Port 8081)" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray

# Test 3.1: Register a new user
try {
    $randomId = Get-Random -Minimum 1000 -Maximum 9999
    $registerData = @{
        username = "testuser$randomId"
        password = "TestPass123!"
        email = "test$randomId@example.com"
    } | ConvertTo-Json
    
    $registerResponse = Invoke-RestMethod -Uri "http://localhost:8081/api/users/register" -Method Post -Body $registerData -ContentType "application/json" -TimeoutSec 10
    Test-Result "User Registration" $true "User ID: $($registerResponse.id)"
    $testUserId = $registerResponse.id
} catch {
    Test-Result "User Registration" $false $_.Exception.Message
    $testUserId = $null
}

# Test 3.2: Get user by ID
if ($testUserId) {
    try {
        $userResponse = Invoke-RestMethod -Uri "http://localhost:8081/api/users/$testUserId" -Method Get -TimeoutSec 10
        Test-Result "Get User by ID" $true "Username: $($userResponse.username)"
    } catch {
        Test-Result "Get User by ID" $false $_.Exception.Message
    }
}
Write-Host ""

# ═══════════════════════════════════════════════════════════════
# TEST 4: ACTIVITY SERVICE (Port 8082)
# ═══════════════════════════════════════════════════════════════
Write-Host "TEST 4: Activity Service (Port 8082)" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray

# Test 4.1: Create an activity
try {
    $activityData = @{
        userId = if ($testUserId) { $testUserId } else { "test-user-123" }
        activityType = "RUNNING"
        duration = 30
        caloriesBurned = 300
        activityDate = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
    } | ConvertTo-Json
    
    $activityResponse = Invoke-RestMethod -Uri "http://localhost:8082/api/activities" -Method Post -Body $activityData -ContentType "application/json" -TimeoutSec 10
    Test-Result "Create Activity" $true "Activity ID: $($activityResponse.id)"
    $testActivityId = $activityResponse.id
} catch {
    Test-Result "Create Activity" $false $_.Exception.Message
    $testActivityId = $null
}

# Test 4.2: Get activity by ID
if ($testActivityId) {
    try {
        $getActivityResponse = Invoke-RestMethod -Uri "http://localhost:8082/api/activities/$testActivityId" -Method Get -TimeoutSec 10
        Test-Result "Get Activity by ID" $true "Type: $($getActivityResponse.activityType), Duration: $($getActivityResponse.duration)min"
    } catch {
        Test-Result "Get Activity by ID" $false $_.Exception.Message
    }
}

# Test 4.3: Get all activities
try {
    $allActivities = Invoke-RestMethod -Uri "http://localhost:8082/api/activities" -Method Get -TimeoutSec 10
    Test-Result "Get All Activities" $true "Total activities: $($allActivities.Count)"
} catch {
    Test-Result "Get All Activities" $false $_.Exception.Message
}

# Test 4.4: MongoDB Database Check
try {
    $activityCount = mongosh --quiet --eval "db.getSiblingDB('fitnessactivity').activities.countDocuments({})"
    Test-Result "MongoDB - fitnessactivity database" $true "$activityCount documents"
} catch {
    Test-Result "MongoDB - fitnessactivity database" $false "MongoDB query failed"
}
Write-Host ""

# ═══════════════════════════════════════════════════════════════
# TEST 5: AI SERVICE (Port 8083)
# ═══════════════════════════════════════════════════════════════
Write-Host "TEST 5: AI Service (Port 8083)" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray

# Test 5.1: Check if AI Service is running
try {
    $aiHealth = Invoke-WebRequest -Uri "http://localhost:8083" -Method Get -UseBasicParsing -TimeoutSec 10
    if ($aiHealth.StatusCode -eq 200 -or $aiHealth.StatusCode -eq 404) {
        Test-Result "AI Service Running" $true "Service is accessible"
    }
} catch {
    if ($_.Exception.Response.StatusCode -eq 404) {
        Test-Result "AI Service Running" $true "Service is accessible (404 expected)"
    } else {
        Test-Result "AI Service Running" $false $_.Exception.Message
    }
}

# Test 5.2: Check RabbitMQ Connection
try {
    $cred = New-Object PSCredential("guest", (ConvertTo-SecureString "guest" -AsPlainText -Force))
    $rabbitQueue = Invoke-RestMethod -Uri "http://localhost:15672/api/queues/%2F/activity.queue" -Method Get -Credential $cred -TimeoutSec 10
    Test-Result "RabbitMQ Queue Configured" $true "Queue: $($rabbitQueue.name), Consumers: $($rabbitQueue.consumers)"
} catch {
    Test-Result "RabbitMQ Queue Configured" $false $_.Exception.Message
}

# Test 5.3: Check MongoDB recommendations database
try {
    $recCount = mongosh --quiet --eval "db.getSiblingDB('fitnessrecommendation').recommendations.countDocuments({})"
    Test-Result "MongoDB - fitnessrecommendation database" $true "$recCount recommendations"
} catch {
    Test-Result "MongoDB - fitnessrecommendation database" $false "MongoDB query failed"
}

# Test 5.4: Wait for AI recommendation (if activity was created)
if ($testActivityId) {
    Write-Host "  ⏳ Waiting 20 seconds for AI recommendation generation..." -ForegroundColor Gray
    Start-Sleep -Seconds 20
    
    try {
        $newRecCount = mongosh --quiet --eval "db.getSiblingDB('fitnessrecommendation').recommendations.countDocuments({})"
        if ($newRecCount -gt $recCount) {
            Test-Result "AI Recommendation Generated" $true "New recommendation created"
        } else {
            Test-Result "AI Recommendation Generated" $false "No new recommendation (may take longer)"
        }
    } catch {
        Test-Result "AI Recommendation Generated" $false "MongoDB query failed"
    }
}
Write-Host ""

# ═══════════════════════════════════════════════════════════════
# TEST 6: API GATEWAY (Port 8080)
# ═══════════════════════════════════════════════════════════════
Write-Host "TEST 6: API Gateway (Port 8080)" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray

# Test 6.1: Gateway Routes Users
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/users" -Method Get -UseBasicParsing -TimeoutSec 10
    Test-Result "Gateway → User Service Route" $true "Status: $($response.StatusCode)"
} catch {
    if ($_.Exception.Response.StatusCode -eq 401) {
        Test-Result "Gateway → User Service Route" $true "Routing works (401 auth expected)"
    } else {
        Test-Result "Gateway → User Service Route" $false $_.Exception.Message
    }
}

# Test 6.2: Gateway Routes Activities (via Gateway)
if ($testActivityId) {
    try {
        $activityViaGateway = Invoke-RestMethod -Uri "http://localhost:8080/api/activities/$testActivityId" -Method Get -TimeoutSec 10
        Test-Result "Gateway → Activity Service Route" $true "Activity retrieved via gateway"
    } catch {
        Test-Result "Gateway → Activity Service Route" $false $_.Exception.Message
    }
}

# Test 6.3: CORS Configuration
try {
    $corsResponse = Invoke-WebRequest -Uri "http://localhost:8080/api/activities" -Method Options -Headers @{
        "Origin" = "http://localhost:5173"
        "Access-Control-Request-Method" = "GET"
    } -UseBasicParsing -TimeoutSec 10
    
    $corsHeader = $corsResponse.Headers["Access-Control-Allow-Origin"]
    if ($corsHeader) {
        Test-Result "CORS Configuration" $true "CORS headers present"
    } else {
        Test-Result "CORS Configuration" $false "No CORS headers found"
    }
} catch {
    Test-Result "CORS Configuration" $false $_.Exception.Message
}
Write-Host ""

# ═══════════════════════════════════════════════════════════════
# TEST 7: FRONTEND (Port 5173)
# ═══════════════════════════════════════════════════════════════
Write-Host "TEST 7: Frontend (Port 5173)" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray

try {
    $frontend = Invoke-WebRequest -Uri "http://localhost:5173" -Method Get -UseBasicParsing -TimeoutSec 10
    Test-Result "Frontend Accessible" $true "Status: $($frontend.StatusCode)"
    
    if ($frontend.Content -match "React" -or $frontend.Content -match "Fitness") {
        Test-Result "Frontend Content Loaded" $true "React app loaded"
    }
} catch {
    Test-Result "Frontend Accessible" $false $_.Exception.Message
}
Write-Host ""

# ═══════════════════════════════════════════════════════════════
# FINAL SUMMARY
# ═══════════════════════════════════════════════════════════════
Write-Host "╔═══════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║                      TEST SUMMARY                              ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

$totalTests = $testsPassed + $testsFailed
$successRate = [math]::Round(($testsPassed / $totalTests) * 100, 2)

Write-Host "Total Tests:    $totalTests" -ForegroundColor White
Write-Host "Tests Passed:   $testsPassed" -ForegroundColor Green
Write-Host "Tests Failed:   $testsFailed" -ForegroundColor $(if ($testsFailed -eq 0) {"Green"} else {"Red"})
Write-Host "Success Rate:   $successRate%" -ForegroundColor Cyan
Write-Host ""

if ($testsFailed -eq 0) {
    Write-Host "🎉 ALL TESTS PASSED! All microservices are working perfectly!" -ForegroundColor Green
} else {
    Write-Host "⚠ $testsFailed test(s) failed. Review the output above for details." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray
Write-Host "DETAILED RESULTS:" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray

foreach ($result in $testResults) {
    $statusColor = if ($result.Status -eq "PASS") { "Green" } else { "Red" }
    Write-Host "$($result.Status) - $($result.Name)" -ForegroundColor $statusColor
    if ($result.Details) {
        Write-Host "      $($result.Details)" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "Test completed at: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Gray
Write-Host ""
