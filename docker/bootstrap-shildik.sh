#!/usr/bin/env bash
# The realm, the rider's client and one person — everything a sign-in needs and nothing more.
#
# **Written down because it is otherwise rediscovered.** A public client, a redirect URI that matches
# exactly, and a password set through its own address: three facts that are obvious once known and
# cost an afternoon when they are not.
set -euo pipefail

ADMIN=${ADMIN:-http://127.0.0.1:19001}
TOKEN=${SHILDIK_BOOTSTRAP_TOKEN:-bootstrap}
REALM=${REALM:-shashki}
CLIENT=${CLIENT:-rider}
REDIRECT=${REDIRECT:-http://127.0.0.1:18080/callback}
# **The driver bundle is served under a prefix, so its callback is a second address** (B-52). One
# client and one realm — which bundle somebody opened is which role they are — but a redirect URI is
# matched exactly, and `/callback` is the rider's.
DRIVER_REDIRECT=${DRIVER_REDIRECT:-http://127.0.0.1:18080/driver/callback}
EMAIL=${EMAIL:-rider@example.com}
PASSWORD=${PASSWORD:-correct-horse-battery-staple}

admin() {
  curl -sS -X "$1" "$ADMIN$2" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    ${3:+-d "$3"} -w '\n'
}

echo "realm $REALM"
admin POST /admin/tenants "{\"realm\":\"$REALM\"}" || true

# **Public and with a redirect URI.** A public client has no secret — it is a browser, and everything
# it ships is readable — so PKCE is the only thing protecting the code, and shildik refuses a public
# client that omits it.
echo "client $CLIENT -> $REDIRECT and $DRIVER_REDIRECT"
admin POST "/admin/tenants/$REALM/clients" \
  "{\"clientId\":\"$CLIENT\",\"public\":true,\"redirectUris\":[\"$REDIRECT\",\"$DRIVER_REDIRECT\"]}" || true

echo "user $EMAIL"
admin POST "/admin/tenants/$REALM/users" "{\"id\":\"$EMAIL\",\"email\":\"$EMAIL\"}" || true
admin PUT "/admin/tenants/$REALM/users/$EMAIL/password" "{\"password\":\"$PASSWORD\"}" || true

echo "done"
