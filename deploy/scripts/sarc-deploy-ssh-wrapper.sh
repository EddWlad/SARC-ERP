#!/usr/bin/env bash
# Comando forzado de la clave SSH de despliegue (authorized_keys de la cuenta `sarc-deploy`).
# Se instala como /usr/local/sbin/sarc-deploy-ssh-wrapper (root:root 0755) y se asocia con:
#   command="/usr/local/sbin/sarc-deploy-ssh-wrapper",no-port-forwarding,no-X11-forwarding,no-agent-forwarding,no-pty <clave>
#
# Acepta SOLO una de estas tres órdenes (SSH_ORIGINAL_COMMAND), donde <version> es AAAA.MM.DD-<n>-<sha7>:
#   deploy <version>
#   deploy --primera-instalacion <version>
#   deploy --verificar <version>
# y las ejecuta como `sudo -n /usr/local/sbin/sarc-deploy <argumentos>` (una única regla sudoers).
# Cualquier otra entrada (argumentos extra, ; && | $( ) comillas invertidas, saltos de línea, versión con formato
# incorrecto, modo desconocido o vacía) se rechaza con código 2 y se registra SIN repetir el texto recibido.

orden="${SSH_ORIGINAL_COMMAND-}"
patron='^deploy( --primera-instalacion| --verificar)? ([0-9]{4}\.[0-9]{2}\.[0-9]{2}-[0-9]+-[0-9a-f]{7})$'

if [[ "$orden" =~ $patron ]]; then
	modo="${BASH_REMATCH[1]# }"
	version="${BASH_REMATCH[2]}"
	if [[ -n "$modo" ]]; then
		exec sudo -n /usr/local/sbin/sarc-deploy "$modo" "$version"
	fi
	exec sudo -n /usr/local/sbin/sarc-deploy "$version"
fi

# Rechazo: sin eco de la entrada recibida (podría contener datos o caracteres de control).
logger -t sarc-deploy-wrapper "orden rechazada: no coincide con ninguna forma permitida" 2>/dev/null || true
echo "orden no permitida" >&2
exit 2
