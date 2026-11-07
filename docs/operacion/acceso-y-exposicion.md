# Acceso administrativo y exposición del VPS

Cómo se protege el acceso al servidor y qué queda expuesto a Internet. La regla general: **solo 22, 80 y 443 hacia afuera**; PostgreSQL y los servicios internos no se alcanzan desde Internet.

**Responsable**: responsable técnico. **Requisitos previos**: VPS Ubuntu con acceso de administración inicial, y las claves públicas SSH del responsable y del suplente. Nada de lo siguiente se ejecuta antes de la autorización de la etapa E7 (ver `decisiones-previas.md`).

## Cortafuegos (UFW)

1. Política por omisión: denegar entrada, permitir salida.
2. Permitir solo 22/tcp, 80/tcp y 443/tcp.

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status verbose
```

Resultado esperado: `status verbose` lista únicamente esos tres puertos.

## Docker no debe saltarse el cortafuegos

Docker modifica `iptables` y puede publicar puertos aunque UFW los bloquee. Por eso:

- PostgreSQL se publica solo en `127.0.0.1:5432` (`deploy/compose/compose.prod.yaml`). Nunca `5432:5432`.
- No se publica ningún otro puerto de contenedores fuera de `127.0.0.1`.
- Tras instalar, comprobar desde **otra red** (no desde el VPS) que 5432 y 8080 son rechazados (ver «Comprobar la exposición»).

## SSH

Configuración en `/etc/ssh/sshd_config.d/sarc.conf`:

```text
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
```

Aplicar con `sudo sshd -t && sudo systemctl reload ssh`. Antes de cerrar la sesión actual, abrir otra y comprobar que se puede ingresar con la clave.

## Cuentas

| Cuenta | Para qué | Privilegios |
|---|---|---|
| Una cuenta personal por persona (responsable y suplente) | Administración | `sudo`; ingreso solo con clave |
| `sarc` | Ejecuta `sarc.service` | Sin shell ni ingreso; solo lee `/etc/sarc/sarc.env` (grupo) y escribe en `/var/lib/sarc` |
| `sarc-deploy` | Recibe el despliegue desde GitHub | **Sin privilegios y sin shell**: su clave tiene comando forzado (`sarc-deploy-ssh-wrapper`) que solo acepta `deploy <version>`, `deploy --primera-instalacion <version>` o `deploy --verificar <version>` y lo ejecuta como `sudo -n /usr/local/sbin/sarc-deploy …` gracias a **una única regla `sudoers`** (`deploy/sudoers/sarc-deploy`). Los scripts y sus bibliotecas pertenecen a root (`/usr/local/sbin/` root:root 0755; `/usr/local/lib/sarc/` root:root 0644) y la cuenta no puede modificarlos. |
| `sarc-backup` | Descarga de copias desde el computador local | **Solo SFTP enjaulado**, sin shell; lectura de las copias y escritura **solo** en `received/` |

Instalación del comando forzado y de la regla `sudoers` (resultado esperado: el ingreso con la clave de despliegue ejecuta solo ese comando):

```bash
sudo install -m 0755 -o root -g root deploy/scripts/sarc-deploy-ssh-wrapper.sh /usr/local/sbin/sarc-deploy-ssh-wrapper
sudo visudo -c -f deploy/sudoers/sarc-deploy
sudo install -m 0440 -o root -g root deploy/sudoers/sarc-deploy /etc/sudoers.d/sarc-deploy
```

En `~sarc-deploy/.ssh/authorized_keys` la línea de la clave pública de GitHub lleva el prefijo:

```text
command="/usr/local/sbin/sarc-deploy-ssh-wrapper",no-port-forwarding,no-X11-forwarding,no-agent-forwarding,no-pty <clave pública de despliegue>
```

## Alta, baja y rotación de claves

- **Alta**: añadir la clave pública de la persona a `~/.ssh/authorized_keys` de su cuenta personal (el responsable lo hace con su propia sesión). Registrar fecha y persona.
- **Baja**: eliminar la clave y la cuenta (`sudo deluser --remove-home <cuenta>`), cerrar sus sesiones (`sudo pkill -u <cuenta>`) y comprobar que ya no ingresa. Quitar a la persona de los revisores del entorno `production` y de las comprobaciones de Healthchecks.io.
- **Rotación de la clave de despliegue**: generar un par nuevo, reemplazar la línea de `authorized_keys` de `sarc-deploy`, actualizar el secreto `DEPLOY_SSH_KEY` y confirmar que la clave anterior ya no ingresa.
- **Rotación de la clave de `sarc-backup`**: igual, en `authorized_keys` de esa cuenta y en el computador local.

## Protección contra abusos y actualizaciones

- `fail2ban` con la cárcel `sshd` activa (`sudo fail2ban-client status sshd`).
- `unattended-upgrades` con actualizaciones de seguridad automáticas. Los reinicios que exijan las actualizaciones se planifican en una ventana acordada y se comprueba después `sarc.service`.

## Registros y retención (FR-037)

- La aplicación escribe en `journald`. Configurar en `/etc/systemd/journald.conf.d/sarc.conf`: `MaxRetentionSec=30day` y un tope de tamaño `SystemMaxUse=2G`. Resultado esperado: `journalctl -u sarc --since "-30 days"` muestra el periodo.
- Nginx: `logrotate` con 30 días (`rotate 30`, `daily`, `compress`) en `/etc/logrotate.d/nginx`. Los registros no contienen `Cookie`, `Authorization` ni parámetros de consulta (formato `sarc` de la plantilla).
- **La auditoría de SARC** (tabla `audit_event`) **no se rota ni se borra**.

## Comprobar la exposición

Desde una red externa (no desde el VPS):

```bash
nmap -Pn -p 22,80,443,5432,8080 <IP_PUBLICA>
curl -sk -o /dev/null -w '%{http_code}\n' https://<IP_PUBLICA>/actuator/info
curl -sk https://<IP_PUBLICA>/actuator/health/readiness
```

Resultado esperado: 22, 80 y 443 abiertos; 5432 y 8080 cerrados o filtrados; `/actuator/info` responde 404; `readiness` responde solo `{"status":"UP"}`. Si algo no coincide, no continuar: revisar UFW y Docker.

## Si falla

- No puedes ingresar por SSH tras endurecerlo: usar la consola de rescate del proveedor (Contabo) y revisar `sshd_config.d`.
- Un puerto inesperado aparece abierto: cerrarlo en UFW, revisar `docker ps` por puertos publicados y avisar al responsable.
