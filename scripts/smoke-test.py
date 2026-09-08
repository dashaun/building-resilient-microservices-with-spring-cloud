#!/usr/bin/env python3
"""Check a running local workshop. Adds test votes; requires Python 3 only."""
import json
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor


def request(port, path, data=None, token=None):
    headers = {"Accept": "application/json" if port == 8761 else "*/*"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if data is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(f"http://localhost:{port}{path}",
                                 data=json.dumps(data).encode() if data is not None else None,
                                 headers=headers)
    try:
        response = urllib.request.urlopen(req, timeout=8)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        body = response.read().decode()
        try:
            body = json.loads(body)
        except json.JSONDecodeError:
            pass
        return response.status, body, response.headers


def main():
    # A fresh Eureka cache or first routed request may not be ready immediately.
    for port, path in [(8888, "/survey-service/default"), (8081, "/questions"),
                       (8082, "/questions"), (8083, "/sauce"),
                       (8080, "/survey-service/questions"), (8080, "/"), (8080, "/results")]:
        deadline = time.monotonic() + 90
        while True:
            try:
                if request(port, path)[0] == 200:
                    break
            except (urllib.error.URLError, TimeoutError):
                pass
            if time.monotonic() >= deadline:
                raise AssertionError(f"Not ready: :{port}{path}")
            time.sleep(1)
    config = request(8888, "/survey-service/default")
    assert config[0] == 200 and config[1]["propertySources"]
    for port in (8081, 8082, 8080):
        path = "/questions" if port != 8080 else "/survey-service/questions"
        status, questions, _ = request(port, path)
        assert status == 200 and len(questions) == 3, (port, status, questions)
    apps = request(8761, "/eureka/apps")[1]["applications"]["application"]
    survey = next(app for app in apps if app["name"] == "SURVEY-SERVICE")
    assert len([i for i in survey["instance"] if i["status"] == "UP"]) == 2
    print("PASS config, questions, two discovered survey instances")

    vote = {"questionId": "sauce", "answer": "Spicy", "voterId": "workshop-smoke"}
    assert request(8080, "/survey-service/submit", vote)[0] == 401
    assert request(8080, "/survey-service/submit", vote, "invalid")[0] == 401
    token = request(8080, "/token")[1]["access_token"]
    before = request(8083, "/sauce")[1]["totalResponses"]
    assert request(8080, "/survey-service/submit", vote, token)[0] == 200
    for _ in range(40):
        direct = request(8083, "/sauce")[1]
        if direct["totalResponses"] > before:
            break
        time.sleep(.25)
    assert direct["totalResponses"] > before, direct
    status, resilient, _ = request(8081, "/tally/sauce")
    assert status == 200 and resilient == direct, (resilient, direct)
    print("PASS JWT denial/acceptance, RabbitMQ delivery, matching HTTP tally")

    time.sleep(2)
    with ThreadPoolExecutor(max_workers=20) as pool:
        responses = list(pool.map(lambda _: request(8080, "/survey-service/submit", vote, token), range(30)))
    assert all(r[0] in (200, 429) for r in responses), [r[0] for r in responses]
    assert any(r[0] == 429 and r[2].get("X-RateLimit-Remaining") == "0" for r in responses)
    assert request(8080, "/")[0] == 200
    assert request(8080, "/results")[0] == 200
    print("PASS Redis edge limiting and both UI routes")


if __name__ == "__main__":
    main()
