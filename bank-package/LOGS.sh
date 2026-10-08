#!/usr/bin/env bash
# Follow app logs / SOAP for SoapUI. Callers: bank ops.
# User: "sara code git pe push ... bank ky system pe git se hi deploy"
set -euo pipefail
echo "Tips:"
echo "  Ctrl+C to stop"
echo "  SOAP:   docker logs bml_switch_app 2>&1 | grep -A80 'iMal SOAP REQUEST'"
echo "  Errors: docker logs bml_switch_app 2>&1 | grep -E 'ERROR|FAIL|BUSINESS_ERROR'"
echo
docker logs -f --tail=200 bml_switch_app
