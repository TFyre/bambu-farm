# Docker Compose environment for X1C printers

Docker compose environment for X1C Printers to enable liveview.

> [!IMPORTANT]
> REMEMBER to enable `LAN Mode Liveview` on the printer

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

Enable Proper IP (docker host OR public ip / dns)
```yaml
webrtcAdditionalHosts: [10.0.0.456, my.dynamic.dns.com]
```
To enable access from internet, add your public ip or DNS **OR** to enable access from local lan, add the ip of the docker host.


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
            PRINTER_ID: ([^1])FIXME_this_is_my_printer_id_from_env
            PRINTER_ACCESS_CODE: FIXME_this_is_my_printer_access_code
```

> [!NOTE]
> `PRINTER_ID` is the printer id in the `.env` configuration file eg: 
> ```properties
> bambu.printers.PRINTER_ID.name=My Printer Name
> ```

# Ports required for outside access

| PORT | UDP/TCP | Purpose |
|--|--|--|
|8189|TCP+UDP|Streaming for WebRTC|
|8080|TCP|HTTP for BambuWeb & WebRTC|

# Starting

Start with `docker compose up`

# Links

* https://github.com/bluenviron/mediamtx
* https://docs.linuxserver.io/images/docker-ffmpeg/
