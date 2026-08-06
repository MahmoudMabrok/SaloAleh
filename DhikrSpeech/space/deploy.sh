#!/usr/bin/env bash
#
# Push DhikrSpeech/space to a Hugging Face Space.
#
# A Space is its own git repo, so it cannot import `src/` from the parent folder
# the way a checkout can. This script stages the Space together with the shared
# pipeline code (src/, configs/config.yaml, phrases.json) and pushes that. The
# staged copies never enter this repo - the pipeline has one source of truth.
#
#   export HF_TOKEN=hf_...                       # write token
#   ./deploy.sh <user-or-org>/<space-name>
#   ./deploy.sh <user-or-org>/<space-name> --with-model
#
# --with-model also uploads model/ (exported .tflite + sidecars). Without it the
# Space deploys empty and a model is uploaded through the UI.

set -euo pipefail

SPACE_ID="${1:-${HF_SPACE_ID:-}}"
WITH_MODEL=0
for argument in "${@:2}"; do
  case "$argument" in
    --with-model) WITH_MODEL=1 ;;
    *) echo "unknown option: $argument" >&2; exit 2 ;;
  esac
done

if [[ -z "$SPACE_ID" ]]; then
  echo "usage: $0 <user>/<space> [--with-model]   (or set HF_SPACE_ID)" >&2
  exit 2
fi
if [[ -z "${HF_TOKEN:-}" ]]; then
  echo "HF_TOKEN is not set - create a write token at https://huggingface.co/settings/tokens" >&2
  exit 2
fi

SPACE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SPACE_DIR")"
STAGING="$(mktemp -d)"
trap 'rm -rf "$STAGING"' EXIT

echo "==> staging $SPACE_ID"
git clone --depth 1 "https://oauth2:${HF_TOKEN}@huggingface.co/spaces/${SPACE_ID}" "$STAGING/space" 2>/dev/null || {
  echo "could not clone the Space - create it first at https://huggingface.co/new-space (SDK: gradio)" >&2
  exit 1
}

CHECKOUT="$STAGING/space"
# Everything the Space owns is replaced; .git and any model/ already committed
# there survive so a deploy without --with-model does not wipe the live model.
find "$CHECKOUT" -mindepth 1 -maxdepth 1 \
  ! -name '.git' ! -name 'model' -exec rm -rf {} +

cp "$SPACE_DIR"/{app.py,inference.py,requirements.txt,README.md} "$CHECKOUT/"
cp -r "$PROJECT_DIR/src" "$CHECKOUT/src"
mkdir -p "$CHECKOUT/configs"
cp "$PROJECT_DIR/configs/config.yaml" "$CHECKOUT/configs/config.yaml"
cp "$PROJECT_DIR/phrases.json" "$CHECKOUT/phrases.json"
find "$CHECKOUT/src" -name '__pycache__' -type d -exec rm -rf {} + 2>/dev/null || true

if [[ "$WITH_MODEL" == "1" ]]; then
  mkdir -p "$CHECKOUT/model"
  shopt -s nullglob
  artefacts=("$SPACE_DIR"/model/*.tflite "$SPACE_DIR"/model/labels*.* "$SPACE_DIR"/model/model_meta.json)
  shopt -u nullglob
  if [[ ${#artefacts[@]} -eq 0 ]]; then
    echo "--with-model was passed but model/ holds no export" >&2
    exit 1
  fi
  cp "${artefacts[@]}" "$CHECKOUT/model/"
  # .tflite is binary and can exceed the 10 MB plain-git limit on the Hub.
  ( cd "$CHECKOUT" && git lfs track "*.tflite" >/dev/null 2>&1 || true )
  [[ -f "$CHECKOUT/.gitattributes" ]] && git -C "$CHECKOUT" add .gitattributes
  echo "==> including $(ls "$CHECKOUT/model" | tr '\n' ' ')"
fi

cd "$CHECKOUT"
git add -A
if git diff --cached --quiet; then
  echo "==> nothing changed"
  exit 0
fi
git -c user.email="deploy@dhikrspeech" -c user.name="DhikrSpeech deploy" \
  commit -q -m "Sync DhikrSpeech Space from $(git -C "$PROJECT_DIR" rev-parse --short HEAD 2>/dev/null || echo local)"
git push -q origin HEAD:main

echo "==> https://huggingface.co/spaces/${SPACE_ID}"
