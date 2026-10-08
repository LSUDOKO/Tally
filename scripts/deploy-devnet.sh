#!/usr/bin/env bash
# Build and deploy the Tally program to devnet (or $TALLY_CLUSTER_URL).
# The program keypair lives outside the repo: ~/.config/tally/tally-program-keypair.json
set -euo pipefail
cd "$(dirname "$0")/.."

URL="${TALLY_CLUSTER_URL:-devnet}"
PROGRAM_KEYPAIR="${TALLY_PROGRAM_KEYPAIR:-$HOME/.config/tally/tally-program-keypair.json}"
SO=target/deploy/tally.so

[ -f "$PROGRAM_KEYPAIR" ] || { echo "missing program keypair $PROGRAM_KEYPAIR"; exit 1; }
cargo build-sbf --manifest-path programs/tally/Cargo.toml

PROGRAM_ID=$(solana-keygen pubkey "$PROGRAM_KEYPAIR")
BYTES=$(stat -c%s "$SO")
# Deploy holds program-data rent and a same-size write buffer at once, plus fees.
NEED=$(solana rent -u "$URL" "$BYTES" --lamports | grep -o '[0-9]\+' | head -1)
NEED_SOL=$(python3 -c "print(round(2*$NEED/1e9 + 0.5, 2))")
HAVE_SOL=$(solana balance -u "$URL" | awk '{print $1}')
echo "program $PROGRAM_ID, $BYTES bytes; need ~$NEED_SOL SOL, have $HAVE_SOL SOL"
if python3 -c "import sys; sys.exit(0 if $HAVE_SOL >= $NEED_SOL else 1)"; then
  solana program deploy -u "$URL" "$SO" --program-id "$PROGRAM_KEYPAIR" --with-compute-unit-price 10000
  echo "deployed: https://explorer.solana.com/address/$PROGRAM_ID?cluster=devnet"
else
  echo "insufficient SOL: top up $(solana address) at https://faucet.solana.com and re-run"
  exit 2
fi
