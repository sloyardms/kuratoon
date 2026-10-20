#!/bin/sh
# Renders set_real_ip_from lines from the comma-separated TRUSTED_PROXIES variable.
# envsubst cannot loop over a list, so the official image's entrypoint runs this script
# (from /docker-entrypoint.d/) before it renders the templates.
set -eu

out=/etc/nginx/conf.d/00-real-ip.conf
: > "$out"

proxies=$(printf '%s' "${TRUSTED_PROXIES:-}" | tr ',' ' ')
[ -z "$(printf '%s' "$proxies" | tr -d ' ')" ] && exit 0

for proxy in $proxies; do
    # Only IPv4/IPv6 addresses and CIDRs; anything else would be injected into the config.
    case "$proxy" in
        *[!0-9A-Fa-f:./]*)
            echo "15-real-ip.sh: invalid TRUSTED_PROXIES entry: $proxy" >&2
            exit 1
            ;;
    esac
    echo "set_real_ip_from $proxy;" >> "$out"
done

{
    echo "real_ip_header X-Forwarded-For;"
    echo "real_ip_recursive on;"
} >> "$out"
