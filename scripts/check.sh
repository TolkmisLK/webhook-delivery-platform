#!/usr/bin/env sh
set -eu

# Complete local quality gate / 完整本地质量门禁
(cd backend && mvn -B -ntp verify)
(cd frontend && npm ci && npm run check)
npx --yes @redocly/cli@2.47.0 lint docs/openapi.yaml
