# Certificado HTTPS para la IP del VPS

SARC se publica por **IP pública** con HTTPS. El certificado lo emite Let's Encrypt con el perfil `shortlived` (vigencia de unos 6 días, solo para IP), por lo que la renovación automática es una parte crítica del servicio. Este documento explica cómo emitirlo, renovarlo, vigilarlo y qué hacer si falla.

**Responsable**: responsable técnico. **Requisitos previos**:

- VPS con IP pública fija y puertos 80 y 443 alcanzables desde Internet.
- Nginx instalado con `deploy/nginx/sarc-http.conf.template` para la primera emisión. El desafío se sirve desde `/var/www/certbot`.
- Certbot **5.4 o superior** instalado con `snap` (las versiones anteriores no emiten certificados para IP).
- `sarc-check-cert` y `certbot-deploy-hook` instalados en `/usr/local/sbin/` (root, 0755).

No se asume que el flujo habitual para dominios funcione igual con una IP: se prueba primero con el entorno de pruebas (`--staging`).

## Emisión

1. Instalar la plantilla solo HTTP y comprobar el desafío antes de emitir. Si ya existe una configuración TLS activa, conservarla y usar su ubicación ACME.

```bash
sudo install -d -m 0755 /var/www/certbot/.well-known/acme-challenge
sed 's/<IP_PUBLICA>/<IP real>/g' deploy/nginx/sarc-http.conf.template | sudo tee /etc/nginx/conf.d/sarc.conf >/dev/null
sudo nginx -t && sudo systemctl reload nginx
```

2. Probar emisión con Let's Encrypt de ensayo. Usar un nombre de certificado distinto para que el certificado real tenga su propia ruta.

```bash
sudo certbot certonly --webroot -w /var/www/certbot --ip-address <IP_PUBLICA> --cert-name sarc-ip-ensayo --preferred-profile shortlived --staging
```

   Resultado esperado: certificado de ensayo en `/etc/letsencrypt/live/sarc-ip-ensayo/`. Al verificar la emisión, eliminar exclusivamente esa inscripción con `sudo certbot delete --cert-name sarc-ip-ensayo` para que el temporizador no renueve un certificado que no se usará.

3. Cuando la emisión de pruebas funcione, emitir el certificado reconocido:

```bash
sudo certbot certonly --webroot -w /var/www/certbot --ip-address <IP_PUBLICA> --cert-name <IP_PUBLICA> --preferred-profile shortlived
```

4. Renderizar la plantilla TLS con la IP y recargar:

```bash
sed 's/<IP_PUBLICA>/<IP real>/g' deploy/nginx/sarc.conf.template | sudo tee /etc/nginx/conf.d/sarc.conf >/dev/null
sudo nginx -t && sudo systemctl reload nginx
```

5. Comprobar desde otro equipo que la conexión es segura y sin advertencias en navegadores actuales (resultado esperado: certificado emitido para la IP).

## Renovación automática

- Certbot instalado con `snap` crea su temporizador de renovación (dos veces al día). Con certificados de 6 días hace falta que **corra siempre**: comprobar con `systemctl list-timers | grep certbot`.
- Registrar el hook para recargar Nginx y comprobar el certificado nuevo tras cada renovación:

```bash
sudo install -m 0755 deploy/scripts/certbot-deploy-hook.sh /usr/local/sbin/certbot-deploy-hook
sudo certbot reconfigure --cert-name <IP_PUBLICA> --deploy-hook /usr/local/sbin/certbot-deploy-hook
```

- Probar la renovación sin cambiar nada: `sudo certbot renew --dry-run`. Forzar una renovación real una vez para comprobar todo el camino: `sudo certbot renew --force-renewal`.

## Vigilancia y avisos

| Qué | Cómo | Aviso |
|---|---|---|
| Vigencia y cadena | `sarc-check-cert` diario (`sarc-check-cert.timer`): menos de 72 h, certificado ausente o cadena inválida contra el almacén del sistema (y la IP) | Correo (comprobación `certificado` de Healthchecks.io) |
| Desde Internet | Flujo `sonda-externa` cada 15 minutos con validación TLS | *Issue* `alerta-operativa` y correo |
| Publicación | El paso 3 de `sarc-deploy` falla con código 13 si el certificado es inválido o vence en menos de 72 h | La publicación queda bloqueada |

Mientras el certificado no sea válido, la publicación **no** se presenta como lista.

## Si falla

1. **No se emite**: comprobar que el puerto 80 llega a Nginx y que `/.well-known/acme-challenge/` responde desde Internet. Revisar `journalctl -u snap.certbot.renew` y `/var/log/letsencrypt/letsencrypt.log`.
2. **No renueva**: ejecutar `sudo certbot renew --force-renewal` y observar el error; con vigencia de 6 días el margen es corto, así que avisar al responsable del producto.
3. **Let's Encrypt cambió las condiciones para certificados de IP** o no es posible emitir de forma confiable: no publicar. La alternativa es incorporar un dominio (ver abajo) y se decide con el responsable del producto.

## Incorporar un dominio después

Es un cambio de configuración, sin pérdida de datos ni cambios funcionales:

1. Apuntar el DNS del dominio o subdominio a la IP del VPS.
2. Emitir el certificado para el dominio con el mismo `--webroot` (sin `--ip-address`) y, si se prefiere, perfil de vigencia normal.
3. Cambiar `server_name`, las rutas de certificado y `SARC_PUBLIC_URL` (variable de repositorio) y `SARC_PUBLIC_IP` según corresponda; recargar Nginx.
4. Actualizar `sonda-externa` (variable `SARC_PUBLIC_URL`) y repetir las comprobaciones posteriores de una publicación.
5. Activar `includeSubDomains` en HSTS solo si se controla todo el dominio.

## Pendiente de verificar con el VPS real

La emisión y renovación reales para la IP, la renovación forzada, el vencimiento simulado y la conexión segura en navegadores solo pueden comprobarse con el VPS (ver `pendientes-recursos-reales.md`). En la VM de ensayo se usa una CA de prueba propia únicamente con `SARC_DEPLOY_ENV=ensayo`; en producción ese modo se rechaza.
