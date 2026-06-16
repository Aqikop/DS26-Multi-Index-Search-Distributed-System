@echo off
echo ===================================================
echo CHUONG TRINH DOCKER FULL TEST (AUTO DEPLOY ^& TEST)
echo ===================================================

echo.
echo 1. Build cac file JAR bang Maven...
call mvn clean package -DskipTests

echo.
echo 2. Khoi dong toan bo Cluster bang Docker Compose...
docker-compose down
docker-compose up --build -d

echo.
echo 3. Doi 30 giay cho cac Node khoi dong va san sang...
timeout /t 30 /nobreak > NUL

echo.
echo 4. Ket noi cac Node lai voi nhau (Raft Cluster ^& Assign Workers)...
call setup-cluster.bat

echo.
echo 5. Test chuc nang Ingest Async (Nap mon an dac san la: Che Bot Loc Heo Quay)
powershell -Command "$body = @{ name = 'Roasted Pork Tapioca Dumpling Sweet Soup (Che Bot Loc Heo Quay)'; ingredients = @('200g tapioca starch', '100g roasted pork belly (heo quay), diced', '50g ginger, thinly sliced', '100g rock sugar', '4 cups water', '1 tablespoon roasted sesame seeds', '2 pandan leaves'); cookingMethod = '1. Knead tapioca starch with boiling water to form a smooth dough. 2. Wrap a piece of diced roasted pork belly inside a small piece of dough to make a dumpling. 3. Boil the dumplings until they float, then transfer to a bowl of cold water. 4. In a pot, boil water with rock sugar, ginger, and pandan leaves. 5. Add the cooked dumplings to the sweet soup and simmer for 5 minutes. 6. Serve hot, garnished with roasted sesame seeds.' }; $jsonBody = $body | ConvertTo-Json; $response = Invoke-RestMethod -Uri 'http://localhost:8080/api/dishes' -Method Post -ContentType 'application/json' -Body $jsonBody; Write-Output \"[INFO] Ban mon an thanh cong! Dish ID: $($response.id)\"; Write-Output \"[INFO] Doi 10 giay de ETL Node va Recipe Node xu ly ngam...\"; Start-Sleep -Seconds 10; $status = Invoke-RestMethod -Uri \"http://localhost:8080/get?id=$($response.id)\"; $status | ConvertTo-Json"

echo.
echo 6. Test chuc nang Tim Kiem (Search Async)
powershell -Command "$body = @{ user_query = 'sweet soup with pork' }; $jsonBody = $body | ConvertTo-Json; $reqId = Invoke-RestMethod -Uri 'http://localhost:8080/search' -Method Post -ContentType 'application/json' -Body $jsonBody; Write-Output \"[INFO] Ban yeu cau tim kiem thanh cong! Request ID: $reqId\"; Write-Output \"[INFO] Doi 10 giay de LLM Node va Recipe Node xu ly ngam...\"; Start-Sleep -Seconds 10; $status = Invoke-RestMethod -Uri \"http://localhost:8080/get?id=$reqId\"; $status | ConvertTo-Json"

echo.
echo ===================================================
echo FULL TEST HOAN TAT!
echo ===================================================
pause
