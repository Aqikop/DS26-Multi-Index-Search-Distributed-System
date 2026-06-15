import urllib.request
import urllib.error
import json
import time

# 1. Clear Coordinator nodes by restarting? I can't restart it here, but let's just apply the correct node again.
req_apply = urllib.request.Request(
    'http://localhost:8080/apply?id=localhost:8120&type=llm',
    method='POST'
)
try:
    urllib.request.urlopen(req_apply)
except Exception as e:
    pass

# 2. Send search request
req_search = urllib.request.Request(
    'http://localhost:8080/search',
    data=json.dumps({"userQuery": "spicy healthy fish"}).encode('utf-8'),
    headers={'Content-Type': 'application/json'},
    method='POST'
)
try:
    with urllib.request.urlopen(req_search) as response:
        req_id = response.read().decode('utf-8')
        print(f"Request ID: {req_id}")
except Exception as e:
    print(f"Search Exception: {e}")
    exit(1)

# 3. Poll for result
for i in range(10):
    time.sleep(2)
    req_get = urllib.request.Request(f'http://localhost:8080/gettest?id={req_id}')
    try:
        with urllib.request.urlopen(req_get) as response:
            data = json.loads(response.read().decode('utf-8'))
            print(f"State: {data['state']}")
            if data['state'] in ['done', 'error']:
                break
    except Exception as e:
        print(f"Get Exception: {e}")
