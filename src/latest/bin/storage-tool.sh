#!/bin/bash

MARATHON_ARGS=""
for a in "$@"; do
  MARATHON_ARGS="${MARATHON_ARGS} $(printf "%q" "$a")"
done

export MARATHON_ARGS
exec amm-1.0.3-2.12 --predef lib/predef.sc --predef-code "assertStoreCompat; help"
