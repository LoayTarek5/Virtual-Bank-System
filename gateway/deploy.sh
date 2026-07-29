#!/usr/bin/env bash
#
# Rebuild the vbank gateway from the definitions in this directory.
#
# Imports the five APIs, publishes them, then imports and publishes the
# vbank API product. Does NOT create applications, subscriptions or keys,
# and never deletes anything.
#
# Assumes the five APIs and the product do NOT already exist in APIM.
# This is a from-scratch rebuild, not an idempotent sync: re-importing an
# existing API returns 409 (900300), so wipe before running. Preflight
# checks this and exits early.
#
# Usage: ./deploy.sh
set -euo pipefail

ENV=local
VERSION=1.0.0
HOST=localhost
GATEWAY_PORT=8243
APIM_PORT=9443
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

APIS=(RegisterAPI LoginAPI ProfileAPI AccountsAPI TransactionsAPI)
PRODUCT=vbank
PRODUCT_DIR="$DIR/api-products/vbank-$VERSION"

DUMMY=00000000-0000-0000-0000-000000000000
VERIFY=(
  "POST|/vbank/users/register"
  "POST|/vbank/users/login"
  "GET|/vbank/users/$DUMMY/profile"
  "GET|/vbank/accounts/$DUMMY"
  "GET|/vbank/accounts/$DUMMY/transactions"
)

say() { printf '\n== %s\n' "$1"; }

wait_for() {                       # wait_for <apis|api-products> <name>
  local kind=$1 name=$2
  for _ in $(seq 30); do
    apictl get "$kind" -e "$ENV" -k 2>/dev/null | grep -q "$name" && return 0
    sleep 1
  done
  return 1
}

# --------------------------------------------------------------- preflight
say "Preflight"

command -v apictl >/dev/null \
  || { echo "apictl is not on PATH"; exit 1; }

timeout 2 bash -c ": < /dev/tcp/$HOST/$APIM_PORT" 2>/dev/null \
  || { echo "APIM is not listening on $APIM_PORT — start ~/wso2am-4.7.0/bin/api-manager.sh"; exit 1; }

for name in "${APIS[@]}"; do
  [ -d "$DIR/apis/$name" ] || { echo "missing $DIR/apis/$name"; exit 1; }
done
[ -d "$PRODUCT_DIR" ] || { echo "missing $PRODUCT_DIR"; exit 1; }

apictl get apis -e "$ENV" -k >/dev/null \
  || { echo "apictl cannot reach '$ENV' — run: apictl login $ENV -k"; exit 1; }

# This is a from-scratch rebuild, not a sync: apictl has no working update path,
# so anything already in APIM must be removed first. Catching it here turns a
# confusing mid-loop 409 into one clear message before anything is written.
existing_apis=$(apictl get apis -e "$ENV" -k | awk 'NR>1 {print $2}')
for name in "${APIS[@]}"; do
  if grep -qx -- "$name" <<<"$existing_apis"; then
    echo "$name already exists in APIM — wipe the gateway before rebuilding"
    exit 1
  fi
done

if apictl get api-products -e "$ENV" -k | awk 'NR>1 {print $2}' | grep -qx -- "$PRODUCT"; then
  echo "product '$PRODUCT' already exists in APIM — wipe the gateway before rebuilding"
  exit 1
fi

echo "ok"

# ------------------------------------------------------------------ import
say "Importing five APIs"
for name in "${APIS[@]}"; do
  apictl import api -f "$DIR/apis/$name" -e "$ENV" -k
done

# The Publisher search index lags the import write, so change-status fired
# immediately after import fails with "Requested API is not available".
say "Waiting for the Publisher index"
for name in "${APIS[@]}"; do
  wait_for apis "$name" || { echo "$name never appeared in the Publisher"; exit 1; }
done
echo "all five indexed"

# ----------------------------------------------------------------- publish
say "Publishing APIs"
for name in "${APIS[@]}"; do
  # apictl change-status exits 0 even on failure, so parse the output.
  out=$(apictl change-status api -a Publish -n "$name" -v "$VERSION" -e "$ENV" -k 2>&1)
  case "$out" in
    *903234*) echo "  $name: already PUBLISHED on import" ;;
    *Error*)  echo "  $name: publish FAILED — $out" ;;
    *)        echo "  $name -> PUBLISHED" ;;
  esac
done

# ----------------------------------------------------------------- product
say "Importing the $PRODUCT product"
apictl import api-product -f "$PRODUCT_DIR" -e "$ENV" -k

wait_for api-products "$PRODUCT" \
  || { echo "$PRODUCT never appeared in the Publisher"; exit 1; }

out=$(apictl change-status api-product -a Publish -n "$PRODUCT" -v "$VERSION" -e "$ENV" -k 2>&1)
case "$out" in
  *903234*) echo "  $PRODUCT: already PUBLISHED on import" ;;
  *Error*)  echo "  $PRODUCT: publish FAILED — $out" ;;
  *)        echo "  $PRODUCT -> PUBLISHED" ;;
esac

# ------------------------------------------------------------------ verify
say "Verifying through the product namespace (401 = routed and secured)"
fail=0
for entry in "${VERIFY[@]}"; do
  verb=${entry%%|*}
  path=${entry#*|}
  code=$(curl -k -s -o /dev/null -w '%{http_code}' -X "$verb" \
           "https://$HOST:$GATEWAY_PORT$path")
  if [ "$code" = "401" ]; then
    printf '  %-4s %-42s %s ok\n' "$verb" "$path" "$code"
  else
    printf '  %-4s %-42s %s FAIL\n' "$verb" "$path" "$code"
    fail=1
  fi
done

[ "$fail" -eq 0 ] || { echo; echo "Rebuild incomplete."; exit 1; }

say "Gateway rebuilt."