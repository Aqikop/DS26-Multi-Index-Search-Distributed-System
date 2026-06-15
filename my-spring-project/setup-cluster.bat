@echo off
echo ===================================================
echo CHUONG TRINH TU DONG KET NOI CLUSTER (DOCKER)
echo ===================================================
echo.
echo 1. Cho doi cac node khoi dong (10 giay)...
timeout /t 10 /nobreak > NUL

echo.
echo 2. Ket noi cac Coordinator voi nhau (Raft Cluster)...
curl -X POST "http://localhost:8081/follow?id=coordinator-1:8080"
echo.
curl -X POST "http://localhost:8082/follow?id=coordinator-1:8080"
echo.

echo.
echo 3. Dang ky 2 LLM Node vao Coordinator-1...
curl -X POST "http://localhost:8080/apply?id=llm-node-1:8120&type=llm"
echo.
curl -X POST "http://localhost:8080/apply?id=llm-node-2:8121&type=llm"
echo.

echo.
echo 4. Dang ky 3 Recipe Node (Database) vao Coordinator-1...
curl -X POST "http://localhost:8080/apply?id=recipe-node-1:8100&type=db"
echo.
curl -X POST "http://localhost:8080/apply?id=recipe-node-2:8101&type=db"
echo.
curl -X POST "http://localhost:8080/apply?id=recipe-node-3:8102&type=db"
echo.

echo.
echo ===================================================
echo KET NOI THANH CONG! He thong da san sang nhan truy van.
echo ===================================================
pause
