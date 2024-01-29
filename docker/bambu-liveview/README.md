# Docker Compose environment for X1C printers

Docker compose environment for X1C Printers to enable liveview.

| **REMEMBER to enable `LAN Mode Liveview` on the printer**

# MediaMTX Container

**Copy `example - mediamtx.yml` to `mediamtx.yml`**

Enable TCP for WebRTC
```yaml
webrtcLocalTCPAddress: :8189
```

Disable Docker Instance IPs
```yaml
webrtcIPsFromInterfaces: no
```

Enable Proper IP
```yaml
webrtcAdditionalHosts: [10.0.0.456, my.dynamic.dns.com]
```

# Adding your printers

**Copy `example - compose.yaml` to `compose.yml`**

Edit `compose.yml` and fix the printers (lines with FIXME)

```yaml
    printer1:
        extends:
            file: common-liveview.yml
            service: liveview
        depends_on:
            - mediamtx
        environment:
            PRINTER_HOST: FIXME_this_is_my_printer_ip_or_host
            PRINTER_DEVICE_ID: FIXME_this_is_my_device_id
            PRINTER_ACCESS_CODE: FIXME_this_is_my_printer_access_code
```

# Ports required for outside access

| PORT | UDP/TCP | Purpose |
|--|--|--|
|8189|UDP|Streaming for WebRTC|
|8189|TCP|Streaming for WebRTC|
|8889|TCP|HTTP for WebRTC|

# Starting

Start with `docker compose up`

# Links

* https://github.com/bluenviron/mediamtx
* https://docs.linuxserver.io/images/docker-ffmpeg/