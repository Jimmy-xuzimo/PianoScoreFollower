"""Minimal Chrome DevTools Protocol client used to poke the live WebView page."""
import json
import sys
import urllib.request

import websocket


def main() -> int:
    expression = sys.argv[1]
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 9222

    with urllib.request.urlopen(f"http://127.0.0.1:{port}/json", timeout=10) as response:
        targets = json.load(response)

    pages = [t for t in targets if t.get("type") == "page"]
    if not pages:
        print("no page target")
        return 1

    ws = websocket.create_connection(
        pages[0]["webSocketDebuggerUrl"], timeout=20, suppress_origin=True
    )
    ws.send(json.dumps({
        "id": 1,
        "method": "Runtime.evaluate",
        "params": {
            "expression": expression,
            "returnByValue": True,
            "awaitPromise": True,
        },
    }))

    while True:
        message = json.loads(ws.recv())
        if message.get("id") == 1:
            break

    ws.close()

    result = message.get("result", {})
    if "exceptionDetails" in result:
        print("EXCEPTION:", json.dumps(result["exceptionDetails"], ensure_ascii=False))
    print(json.dumps(result.get("result", {}).get("value"), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())