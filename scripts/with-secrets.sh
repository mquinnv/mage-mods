#!/bin/sh
# with-secrets.sh CMD [ARGS…] — run CMD with this project's 1Password Environment.
#
# claudemux injects that Environment into the tmux session environment at
# launch and sets CLAUDEMUX_OP_ENV to its id; when it matches, the secrets are
# already present and op — and its per-invocation 1Password approval prompt —
# is skipped. Everywhere else this is exactly the old `op run` invocation.
ENV_ID=j57ux5exhkydokrpegmtwhns24
if [ "$CLAUDEMUX_OP_ENV" = "$ENV_ID" ]; then
  exec "$@"
fi
exec op run --account whiteleaf.1password.com --environment "$ENV_ID" -- "$@"
