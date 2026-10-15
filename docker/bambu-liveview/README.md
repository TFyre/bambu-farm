# Docker Compose environment for X1C printers

Docker compose environment for X1C Printers to enable liveview.

> [!IMPORTANT]
> REMEMBER to enable `LAN Mode Liveview` on the printer

# How it works

mediamtx pulls each printer's RTSPS stream directly. There is **no ffmpeg
container per printer** — mediamtx speaks RTSP itself, applies a read timeout to
the media path, and reconnects a stalled source on its own.

Earlier versions of this setup ran an `ffmpeg` container per printer that
remuxed `rtsps://…:322/streaming/live/1` onto mediamtx. That works, but it fails
silently: ffmpeg's `-timeout` only covers the RTSP control socket, so a printer
that stops sending leaves ffmpeg alive and the container "healthy" with zero
frames flowing. `restart: always` never fires because the process never exits,
and the stream stays frozen until someone restarts it by hand. mediamtx has no
such gap.

# MediaMTX Container

**Copy `example - mediamtx.yml` to `mediamtx.yml`.**

`mediamtx.yml` is a verbatim copy of upstream's file and is meant to stay that
way. Every local setting lives in an env file instead, because mediamtx applies
`MTX_*` variables *after* reading the YAML — so they always win, and the YAML can
be replaced wholesale on a version bump. See [Upgrading mediamtx](#upgrading-mediamtx).

Configure the following in `mediamtx.env` (copy from `example - mediamtx.env`):

```properties
# Bind WebRTC to a fixed TCP port so it can be reached through the port mapping
MTX_WEBRTCLOCALTCPADDRESS=:8189

# Do not advertise the container's internal IPs as ICE candidates
MTX_WEBRTCIPSFROMINTERFACES=no

# The address browsers should use - the docker host's IP or DNS name
MTX_WEBRTCADDITIONALHOSTS=my.docker.host.ip

# Pull printers over TCP: UDP has no read timeout, so a stalled printer is
# undetectable there and the stream hangs silently
MTX_PATHDEFAULTS_RTSPTRANSPORT=tcp
```

> [!NOTE]
> `sourceOnDemand` **cannot** be set in `pathDefaults`. mediamtx rejects it at
> startup with `'sourceOnDemand' is useless when source is 'publisher'`, because
> `pathDefaults.source` is `publisher`. Set it per path if you want it — see
> `example - mediamtx.env` for how.

# Printers

**Copy `example - printers.list` to `printers.list`** and list your printers:

```
# <name> <ip> <access-code>
myprinter1   192.168.1.50  12345678
myprinter2   192.168.1.51  87654321
```

`name` is the key in `bambu.printers.<id>` — bambu-web builds the live view url as
`bambu.live-view-url` + `id`, so it is also the mediamtx path name. It must be a
**single word with no underscores**: mediamtx takes only the first `_`-delimited
segment of an env var as the map key, so `my_printer` would silently create a
path called `my`.

Then generate `printers.env` from it:

```bash
chmod +x *.sh
./build-printers-env.sh
```

This contacts each printer to fetch its certificate fingerprint and writes one
`MTX_PATHS_<NAME>_*` block per printer.

## The certificate fingerprint

**This part is not optional.** Bambu printers use self-signed certificates, and
mediamtx falls back to normal CA verification when `sourceFingerprint` is empty —
so without it, every connection is rejected.

`build-printers-env.sh` extracts them for you. To do one by hand:

```bash
./extract-fingerprint.sh 192.168.1.50
```

The value is the sha256 of the certificate, lower case hex, no colons and no
`sha256:` prefix — which is exactly what mediamtx compares against.

Fingerprints are per-printer unless your printers all share one certificate. If
they do, you will see the same value repeated and can collapse them. If a printer
is ever updated and its certificate changes, mediamtx logs
`source fingerprint does not match` — re-run `./build-printers-env.sh`.

# Bambu Web

* Download the [latest](https://github.com/TFyre/bambu-farm/releases/latest) version of bambu-web-X.X.X-runner.jar

* `bambu-web-env.txt` is the config file instead of normal `.env`
* Update `compose.yml` and replace `bambu-web-X.X.X-runner.jar` with the correct version

Enable the following in `bambu-web-env.txt`:
```properties
bambu.live-view-url=/_camerastream/

#For Each Printer:
bambu.printers.PRINTER_ID.stream.live-view=true
```

If you have a full custom url for the printer:
```properties
bambu.printers.PRINTER_ID.stream.url=https://my_stream_domain.com/mystream
```

> [!NOTE]
> The live view only appears once mediamtx has the source ready. Check with
> `docker compose logs -f mediamtx` — a working path logs
> `[path myprinter1] [RTSP source] ready: 1 track (H264)`.

## Uploading bigger files

Add to `.env`:
```properties
quarkus.http.limits.max-body-size=30M
```

also remember to update `reverse-proxy.conf`:
```conf
    location / {
        client_max_body_size 30m;
        proxy_pass http://bambuweb;
```

# HTTPS with Let's Encrypt

The `example - compose.yml` includes nginx with TLS and a certbot container for automatic renewal.

1. Copy `example - .env` to `.env` and fix the values (docker compose reads this automatically):
   ```properties
   DOMAIN=your.dynamic.dns.com
   EMAIL=you@example.com
   ```

2. The domain must resolve (DNS) to this host's public IP, and ports `80`/`443` must be reachable from the internet.

3. Before the first `docker compose up`, issue the certificate (nginx can only start once the certificate exists):
   ```bash
   docker run --rm -p 80:80 \
     -v "$(pwd)"/certbot/conf:/etc/letsencrypt \
     -v "$(pwd)"/certbot/www:/var/www/certbot \
     certbot/certbot certonly \
     --standalone --preferred-challenges http \
     -d "$DOMAIN" --email "$EMAIL" \
     --agree-tos --no-eff-email
   ```

4. Start the stack with `docker compose up -d`. nginx now serves `https://$DOMAIN` and redirects HTTP to HTTPS.

5. Renewal is automatic: the certbot container attempts `certbot renew` every 12h (webroot challenge, served by nginx), and nginx reloads every 6h to pick up renewed certificates.

> [!NOTE]
> WebRTC media still flows directly to mediamtx on port `8189` (TCP+UDP) — keep it open and make sure `MTX_WEBRTCADDITIONALHOSTS` in `mediamtx.env` includes your public domain/dns.

> [!NOTE]
> Without TLS you can still use the stack locally: uncomment the `8080:80` port mapping on the nginx service.

# Ports required for outside access

| PORT | UDP/TCP | Purpose |
|--|--|--|
|8189|TCP+UDP|Streaming for WebRTC|
|80|TCP|HTTP (ACME challenge + redirect to HTTPS)|
|443|TCP|HTTPS for BambuWeb & WebRTC|
|8080|TCP|Optional HTTP without TLS (LAN only)|

# Starting

Start with `docker compose up`

# Migrating from the ffmpeg setup

Earlier versions ran one `ffmpeg` container per printer, configured through a
`printerN` service in `compose.yml` that extended `common-liveview.yml`. That
file has been removed. To move across:

1. Delete every `printerN` service from `compose.yml`.
2. Add the `env_file:` block to the mediamtx service (see `example - compose.yml`).
3. Replace `mediamtx.yml` with `example - mediamtx.yml`.
4. Create `mediamtx.env` from `example - mediamtx.env`, and put your printers in
   `printers.list`.
5. `./build-printers-env.sh`, then `docker compose up -d --remove-orphans`.

**`--remove-orphans` matters.** A plain `up` leaves the old `printerN` containers
running, and they keep holding RTSP sessions with your printers — which can stop
mediamtx from pulling them at all. If mediamtx connects but no video arrives,
this is the first thing to check.

Your `PRINTER_HOST` / `PRINTER_ACCESS_CODE` values move from `compose.yml` to
`printers.list`. `bambu-web-env.txt` and `reverse-proxy.conf` need no change.

# Upgrading mediamtx

Because every local setting lives in an env file, a version bump is a new YAML
file and a new image tag, nothing else:

```bash
# GitHub release tags carry a 'v' prefix; Docker Hub image tags do not.
V=v1.21.2
IMG=${V#v}
curl -o "example - mediamtx.yml" "https://raw.githubusercontent.com/bluenviron/mediamtx/$V/mediamtx.yml"
cp "example - mediamtx.yml" mediamtx.yml
sed -i "s|mediamtx:[0-9.]*|mediamtx:$IMG|" compose.yml
docker compose up -d
```

**Pin a release tag — don't use `:latest`.** `docker compose up -d` should be
reproducible, and `latest` changes underneath you.

## Adding or removing printers

Edit `printers.list`, re-run `./build-printers-env.sh`, then
`docker compose up -d mediamtx` to reload the environment.

Env vars are read only at startup, so a new printer needs the container
recreated — `mediamtx.yml` itself hot-reloads, but the env files do not.

# Links

* https://github.com/bluenviron/mediamtx
