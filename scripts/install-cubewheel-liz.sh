#!/usr/bin/env bash
#
# install-cubewheel-liz.sh - push a freshly built CubeWheel jar to Liz's PC.
#
# Usage: scripts/install-cubewheel-liz.sh [path/to/cubewheel-x.y.z.jar]
#        bun run install:liz
# With no argument the newest src/client-mods/cubewheel/build/libs/cubewheel-*.jar
# (by mtime) is installed.
#
# How it works
#   1. The jar is uploaded to a private S3 bucket in Michael's PERSONAL AWS
#      account (profile "mage-server", bucket mage-mods-deploy-013141018003,
#      us-east-1, 7-day lifecycle expiry) and a 15-minute presigned URL is minted.
#   2. An SSM RunPowerShellScript command is sent to Liz's Windows 11 PC, which
#      is an SSM managed instance (mi-0b2dece1d30b6e5db) in the AMERIGLIDE AWS
#      account (profile "ag-aws"). SSM runs the script as SYSTEM. The script
#      downloads the jar from the presigned URL into C:\ProgramData\cubewheel-stage,
#      verifies its SHA-256, copies it into her Prism instance's mods folder, and
#      deletes every other cubewheel-*.jar there so only one version is loaded.
#   3. This script polls the invocation until it finishes, prints her stdout, and
#      verifies the "installed=<jar> sha=<hash>" line matches the local hash.
#
# Two profiles because the two sides live in different AWS accounts: the SSM
# agent on her PC is registered with AmeriGlide (ag-aws), while the transfer
# bucket is Michael's own (mage-server). SSM has no file-transfer primitive of
# its own; the previous route base64-chunked the jar through many RunPowerShell
# commands and took 5-10 minutes per install. This route takes seconds.
#
# javaw guard: overwriting a jar that a running Minecraft has loaded crashes the
# game, so the remote script checks for a javaw process before the download and
# again right before the swap. If javaw is running it prints "javaw=True" and
# exits 0 without touching the mods folder; this script then reports that her
# game is running and nothing was changed. Rerun once she has quit.
#
# Offline guard: SSM accepts a command for an offline instance and leaves it
# Pending (or fails it as "Undeliverable" once the agent's connection is marked
# lost), which looks like a broken install. So the agent's PingStatus is checked
# first; unless it is Online the script reports that her PC is off and exits 0
# without uploading anything.
#
# ASCII rule: everything sent to PowerShell (code and comments) must be PURE
# ASCII. SSM/PowerShell mangle non-ASCII (em dashes, curly quotes, ellipses).
# The parameter JSON is built with python3 json.dumps, never by hand, so the
# presigned URL and the paths are escaped safely, and the PowerShell lines are
# asserted ASCII before they are sent.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
LIBS_DIR="${REPO_ROOT}/src/client-mods/cubewheel/build/libs"

# Transfer bucket (Michael's personal account).
S3_PROFILE="mage-server"
S3_REGION="us-east-1"
S3_BUCKET="mage-mods-deploy-013141018003"
S3_PREFIX="cubewheel"
PRESIGN_SECONDS=900

# Liz's PC (AmeriGlide account).
SSM_PROFILE="ag-aws"
SSM_REGION="us-east-1"
SSM_INSTANCE="mi-0b2dece1d30b6e5db"
POLL_SECONDS=3
POLL_MAX_SECONDS=300

# Paths on her PC (Windows). Kept here, not in the mod, so the published mod
# carries nothing about her machine.
REMOTE_MODS='C:\Users\lizwi\AppData\Roaming\PrismLauncher\instances\Realistic for Public Servers with Stupid Rules\minecraft\mods'
REMOTE_STAGE='C:\ProgramData\cubewheel-stage'

log() { printf '%s %s\n' "$(date +%H:%M:%S)" "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# --- 1. pick the jar -------------------------------------------------------
if [ "$#" -gt 1 ]; then
  die "usage: $(basename "$0") [path/to/cubewheel-x.y.z.jar]"
fi
if [ "$#" -eq 1 ]; then
  JAR_PATH="$1"
else
  JAR_PATH="$(ls -t "${LIBS_DIR}"/cubewheel-*.jar 2>/dev/null | head -n 1 || true)"
  [ -n "${JAR_PATH}" ] || die "no cubewheel-*.jar in ${LIBS_DIR}; build it first"
fi
[ -f "${JAR_PATH}" ] || die "jar not found: ${JAR_PATH}"
JAR_NAME="$(basename "${JAR_PATH}")"
case "${JAR_NAME}" in
  cubewheel-*.jar) ;;
  *) die "expected a cubewheel-*.jar, got ${JAR_NAME}" ;;
esac

SHA_LOCAL="$(shasum -a 256 "${JAR_PATH}" | awk '{print $1}' | tr 'a-f' 'A-F')"
log "jar     ${JAR_PATH}"
log "sha256  ${SHA_LOCAL}"

# --- 2. is her PC reachable? -----------------------------------------------
PING_INFO="$(aws --profile "${SSM_PROFILE}" --region "${SSM_REGION}" ssm describe-instance-information \
  --filters "Key=InstanceIds,Values=${SSM_INSTANCE}" \
  --query 'InstanceInformationList[0].[PingStatus,LastPingDateTime]' --output text)"
PING_STATUS="${PING_INFO%%	*}"
PING_LAST="${PING_INFO#*	}"
if [ "${PING_STATUS}" != "Online" ]; then
  echo "SUMMARY: her PC is offline (SSM PingStatus ${PING_STATUS:-unknown}, last ping ${PING_LAST:-unknown}); nothing changed; rerun when it is on."
  exit 0
fi
log "ssm     agent Online (last ping ${PING_LAST})"

# --- 3. upload + presign ---------------------------------------------------
S3_URI="s3://${S3_BUCKET}/${S3_PREFIX}/${JAR_NAME}"
log "upload  ${S3_URI}"
aws --profile "${S3_PROFILE}" --region "${S3_REGION}" s3 cp "${JAR_PATH}" "${S3_URI}" --only-show-errors
URL="$(aws --profile "${S3_PROFILE}" --region "${S3_REGION}" s3 presign "${S3_URI}" --expires-in "${PRESIGN_SECONDS}")"
[ -n "${URL}" ] || die "presign returned nothing"
log "presign ok (${PRESIGN_SECONDS}s)"

# --- 4. build the SSM parameter file ---------------------------------------
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT
PARAMS_FILE="${TMP_DIR}/params.json"

python3 - "${URL}" "${JAR_NAME}" "${SHA_LOCAL}" "${REMOTE_MODS}" "${REMOTE_STAGE}" > "${PARAMS_FILE}" <<'PY'
import json
import sys

url, jar, sha, mods, stage = sys.argv[1:6]

def ps_str(value):
    # PowerShell single-quoted literal: only the quote itself needs escaping.
    return "'" + value.replace("'", "''") + "'"

lines = [
    "$ErrorActionPreference = 'Stop'",
    "$url = " + ps_str(url),
    "$jar = " + ps_str(jar),
    "$expected = " + ps_str(sha),
    "$mods = " + ps_str(mods),
    "$stage = " + ps_str(stage),
    # Guard 1: never download over a running game.
    "if (Get-Process javaw -ErrorAction SilentlyContinue) { Write-Output 'javaw=True'; exit 0 }",
    "Write-Output 'javaw=False'",
    "New-Item -ItemType Directory -Force -Path $stage | Out-Null",
    "$staged = Join-Path $stage $jar",
    "Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $staged",
    "$got = (Get-FileHash -Algorithm SHA256 -Path $staged).Hash",
    "if ($got -ne $expected) { Write-Output ('hash=MISMATCH expected ' + $expected + ' got ' + $got); Remove-Item -Force $staged; exit 1 }",
    "Write-Output 'hash=OK'",
    # Guard 2: she may have launched the game during the download.
    "if (Get-Process javaw -ErrorAction SilentlyContinue) { Write-Output 'javaw=True'; Remove-Item -Force $staged; exit 0 }",
    "$target = Join-Path $mods $jar",
    "Copy-Item -Force -Path $staged -Destination $target",
    "Get-ChildItem -Path $mods -Filter 'cubewheel-*.jar' | Where-Object { $_.Name -ne $jar } | Remove-Item -Force",
    "Write-Output ('installed=' + $jar + ' sha=' + (Get-FileHash -Algorithm SHA256 -Path $target).Hash)",
    "Write-Output ('remaining=' + ((Get-ChildItem -Path $mods -Filter 'cubewheel-*.jar' | Select-Object -ExpandProperty Name) -join ','))",
    "Remove-Item -Force $staged",
]

for line in lines:
    if not line.isascii():
        sys.exit("non-ASCII character in PowerShell line: " + repr(line))

print(json.dumps({"commands": lines}))
PY

# --- 5. send + poll --------------------------------------------------------
log "ssm     send-command to ${SSM_INSTANCE}"
COMMAND_ID="$(aws --profile "${SSM_PROFILE}" --region "${SSM_REGION}" ssm send-command \
  --document-name AWS-RunPowerShellScript \
  --instance-ids "${SSM_INSTANCE}" \
  --parameters "file://${PARAMS_FILE}" \
  --comment "install ${JAR_NAME}" \
  --query 'Command.CommandId' --output text)"
[ -n "${COMMAND_ID}" ] || die "send-command returned no CommandId"
log "ssm     command ${COMMAND_ID}"

INVOCATION_FILE="${TMP_DIR}/invocation.json"
STATUS="Pending"
DETAILS=""
ELAPSED=0
while :; do
  sleep "${POLL_SECONDS}"
  ELAPSED=$((ELAPSED + POLL_SECONDS))
  # The invocation can take a moment to exist after send-command; tolerate that.
  if aws --profile "${SSM_PROFILE}" --region "${SSM_REGION}" ssm get-command-invocation \
       --instance-id "${SSM_INSTANCE}" --command-id "${COMMAND_ID}" \
       --output json > "${INVOCATION_FILE}" 2>/dev/null; then
    STATUS="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["Status"])' "${INVOCATION_FILE}")"
    DETAILS="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("StatusDetails",""))' "${INVOCATION_FILE}")"
  fi
  case "${STATUS}" in
    Success|Failed|Cancelled|TimedOut) break ;;
  esac
  if [ "${ELAPSED}" -ge "${POLL_MAX_SECONDS}" ]; then
    die "gave up after ${POLL_MAX_SECONDS}s; last status ${STATUS} (command ${COMMAND_ID})"
  fi
done
log "ssm     status ${STATUS} (${DETAILS}) after ${ELAPSED}s"

REMOTE_OUT="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("StandardOutputContent",""), end="")' "${INVOCATION_FILE}")"
REMOTE_ERR="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("StandardErrorContent",""), end="")' "${INVOCATION_FILE}")"

echo "----- remote stdout -----"
printf '%s\n' "${REMOTE_OUT}"
if [ -n "${REMOTE_ERR}" ]; then
  echo "----- remote stderr -----"
  printf '%s\n' "${REMOTE_ERR}"
fi
echo "-------------------------"

# --- 6. verdict ------------------------------------------------------------
if printf '%s' "${REMOTE_OUT}" | grep -q 'javaw=True'; then
  echo "SUMMARY: her game is running (javaw=True); nothing changed; rerun when it is off."
  exit 0
fi
if [ "${STATUS}" = "Success" ] \
   && printf '%s' "${REMOTE_OUT}" | grep -q "installed=${JAR_NAME} sha=${SHA_LOCAL}"; then
  echo "SUMMARY: installed ${JAR_NAME} (sha256 ${SHA_LOCAL}) on Liz's PC in ${ELAPSED}s."
  exit 0
fi
if [ "${DETAILS}" = "Undeliverable" ]; then
  echo "SUMMARY: install NOT delivered; her PC dropped offline (SSM ${STATUS}/${DETAILS}); nothing changed; rerun when it is on." >&2
  exit 1
fi
echo "SUMMARY: install NOT verified (status ${STATUS}/${DETAILS}); see remote output above." >&2
exit 1
