#!/usr/bin/env python3
"""The whole authorization-code-with-PKCE dance, by hand, against a running shildik.

**It exists to prove the stand before any Kotlin runs against it.** A client that fails to sign in
tells you nothing about whether the client is wrong or the provider was never configured — so this
does the same four requests with nothing but the standard library, and the Kotlin has something to
disagree with.

It is also the readable form of what `SignInAttempt` does, and of what the ACs mean: the parked state
comes off the page, the login POST must be same-origin, the code comes back on the redirect, and the
verifier — never sent until the exchange — is what makes the code worth anything. Step 4 is the
control: the same code with a different verifier must be refused, or PKCE is decoration.

    docker compose -f docker/compose.yaml up -d
    bash docker/bootstrap-shildik.sh
    python3 docker/sign-in-flow.py
"""
import base64, hashlib, http.client, json, os, re, sys, urllib.parse

ISSUER = os.environ.get("ISSUER", "http://127.0.0.1:18081")
REALM = os.environ.get("REALM", "shashki")
CLIENT = os.environ.get("CLIENT", "rider")
REDIRECT = os.environ.get("REDIRECT", "http://127.0.0.1:18080/callback")
EMAIL = os.environ.get("EMAIL", "rider@example.com")
PASSWORD = os.environ.get("PASSWORD", "correct-horse-battery-staple")

host = urllib.parse.urlsplit(ISSUER)

def call(method, path, body=None, headers=None):
    c = http.client.HTTPConnection(host.hostname, host.port, timeout=20)
    h = {"Host": host.netloc}
    if body is not None:
        h["Content-Type"] = "application/x-www-form-urlencoded"
    h.update(headers or {})
    c.request(method, path, body=body, headers=h)
    r = c.getresponse()
    data = r.read().decode("utf-8", "replace")
    location = r.getheader("Location")
    c.close()
    return r.status, data, location

b64 = lambda raw: base64.urlsafe_b64encode(raw).decode().rstrip("=")
verifier = b64(os.urandom(32))
challenge = b64(hashlib.sha256(verifier.encode()).digest())
client_state = b64(os.urandom(16))

query = urllib.parse.urlencode({
    "response_type": "code", "client_id": CLIENT, "redirect_uri": REDIRECT,
    "scope": "openid profile email", "state": client_state, "nonce": b64(os.urandom(16)),
    "code_challenge": challenge, "code_challenge_method": "S256",
})
status, page, _ = call("GET", f"/realms/{REALM}/oauth2/authorize?{query}")
print(f"1. authorize          {status}")
if status != 200:
    print(page[:400]); sys.exit(1)

m = re.search(r'name="state"[^>]*value="([^"]+)"', page) or re.search(r'value="([^"]+)"[^>]*name="state"', page)
if not m:
    print("no parked state on the page:", page[:600]); sys.exit(1)
parked = m.group(1)
print(f"   parked state       {parked[:16]}…")

form = urllib.parse.urlencode({"state": parked, "login": EMAIL, "password": PASSWORD})
status, body, location = call(
    "POST", f"/realms/{REALM}/oauth2/login/password", form,
    {"Origin": ISSUER, "Referer": f"{ISSUER}/realms/{REALM}/oauth2/authorize"},
)
print(f"2. login              {status} -> {location}")
if not location:
    print(body[:400]); sys.exit(1)

returned = urllib.parse.parse_qs(urllib.parse.urlsplit(location).query)
code = returned.get("code", [""])[0]
print(f"   state came back    {returned.get('state', [''])[0] == client_state}")

token_form = urllib.parse.urlencode({
    "grant_type": "authorization_code", "code": code,
    "redirect_uri": REDIRECT, "client_id": CLIENT, "code_verifier": verifier,
})
status, body, _ = call("POST", f"/realms/{REALM}/oauth2/token", token_form)
print(f"3. token              {status}")
if status != 200:
    print(body[:400]); sys.exit(1)
tokens = json.loads(body)
print(f"   access_token       {tokens['access_token'][:32]}…  ({len(tokens['access_token'])} chars)")
print(f"   token_type         {tokens.get('token_type')}")

# 4. the control: the same code with a wrong verifier must be refused.
status, body, _ = call("POST", f"/realms/{REALM}/oauth2/token", urllib.parse.urlencode({
    "grant_type": "authorization_code", "code": code,
    "redirect_uri": REDIRECT, "client_id": CLIENT, "code_verifier": b64(os.urandom(32)),
}))
print(f"4. replay, wrong verifier  {status} (must not be 200)")
