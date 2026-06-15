import urllib.request
import urllib.error
import json

req = urllib.request.Request(
    'http://localhost:8120/llm/decompose',
    data=json.dumps({"user_query": "spicy healthy fish"}).encode('utf-8'),
    headers={'Content-Type': 'application/json'}
)

try:
    with urllib.request.urlopen(req) as response:
        print("Success:")
        print(response.read().decode('utf-8'))
except urllib.error.HTTPError as e:
    print(f"HTTPError: {e.code}")
    print(e.read().decode('utf-8'))
except Exception as e:
    print(f"Exception: {e}")
