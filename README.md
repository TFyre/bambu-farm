# Cannot print with latest firmware
> [!IMPORTANT]  
> https://wiki.bambulab.com/en/p1/manual/p1p-firmware-release-history
>
> Bambulab decided to block printing via MQTT unless you enable lanmode only.
>
> Consider downgrading firmware Reference [!142](https://github.com/TFyre/bambu-farm/issues/142)
>
> **OR**
>
> Check the [Cloud Section](#cloud-section) about enabling cloud mode


# Bambu Farm
[![ko-fi](https://img.shields.io/static/v1?label=Support+me+on&message=Ko-fi&logo=ko-fi&color=%23FF5E5B&style=for-the-badge)](https://ko-fi.com/tfyre)
[![GitHub](https://img.shields.io/static/v1?label=Sponsor+me+on&message=%E2%9D%A4&logo=GitHub&color=%23fe8e86&style=for-the-badge)](https://github.com/sponsors/TFyre)

Web based application to monitor multiple bambu printers using mqtt / ftp / rtsp (**no custom firmware required**)

Technologies used:
* Java 21 https://www.azul.com/
* Quarkus https://quarkus.io/
* Vaadin https://vaadin.com/

# Features / Supported Devices

| Feature | A1 | A1 Mini | P1P | P1S | X1C|
|--|:--:|:--:|:--:|:--:|:--:|
|**Remote View**|<ul><li>[x] </li></ul>|<ul><li>[x] </li></ul>|<ul><li>[x] </li></ul>|<ul><li>[x] </li></ul>|<ul><li>[x] <sup>3</sup></li></ul>|
|**Upload to SD card**|<ul><li>[x] </li></ul>|<ul><li>[x] </li></ul>|<ul><li>[x] </li></ul>|<ul><li>[x] </li></ul>|<ul><li>[x] <sup>2</sup></li></ul>|
|**Print .3mf from SD card**<sup>1</sup>|<ul><li>[x] </li></ul>|<ul><li>[x] </li></ul>|<ul><li>[x] </li></ul>|<ul><li>[x] </li></ul>|<ul><li>[x] <sup>2</sup></li></ul>|
|**Print .gcode from SD card**|?|?|?|?|?|
|**Batch Printing**<sup>4</sup>|?|?|?|<ul><li>[x] </li></ul>|<ul><li>[x] <sup>2</sup></li></ul>|
|**AMS**|?|?|?|<ul><li>[x] </li></ul>|<ul><li>[x] </li></ul>|
|**Send Custom GCode**|<ul><li>[x] </li></ul>|<ul><li>[x] </li></ul>|<ul><li>[x] </li></ul>|<ul><li>[x] </li></ul>|<ul><li>[x] </li></ul>|

1. **Currently only .3mf sliced projects are supported.**
  > In Bambu Studio/Orca slicer, make sure to slice the place and then use the "File -> Export -> Export plate sliced file". This creates a `.3mf` project with embedded `.gcode` plate.
2. **FTPS Connections needs SSL Session Reuse via [Bouncy Castle](#bouncy-castle)**
> Without enabling bouncy castle, you will see `552 SSL connection failed: session resuse required`
3. Getting the **LiveView** to work requires additional software. For more details check the [docker/bambu-liveview](docker/bambu-liveview) README.
4. **Batch Priting** allows you to upload a single/multi sliced .3mf and select which plate to send to multiple printers, each with their own filament mapping.

# Screenshots

* Dashboard
![Desktop browser](/docs/bambufarm1.jpg)
* Batch printing
![Batch Printing](/docs/batchprint.png)

*More screenshots in [docs](/docs)*

# I just want to run it

* Make sure you have Java 21 installed, verify with `java -version`
```bash
[user@build:~]# java -version
openjdk version "21.0.1" 2023-10-17 LTS
OpenJDK Runtime Environment Zulu21.30+15-CA (build 21.0.1+12-LTS)
OpenJDK 64-Bit Server VM Zulu21.30+15-CA (build 21.0.1+12-LTS, mixed mode, sharing)
```
* Download the latest `bambu-web-*-runner.jar` from [releases](https://github.com/TFyre/bambu-farm/releases/latest) into a new folder (or use the 1 liner below):
```bash
curl -s https://api.github.com/repos/tfyre/bambu-farm/releases/latest \
  | grep browser_download_url | cut -d'"' -f4 | xargs curl -LO
```
* Create a `.env` config file from [Minimal Config](#minimal-config)
  * *Check out the [Full Config Options](#full-config-options) section if you want to tweak some settings*
* Run with `java -jar bambu-web-x.x.x-runner.jar`
```bash
[user@build:~]# java -jar bambu-web-1.0.1-runner.jar
__  ____  __  _____   ___  __ ____  ______
 --/ __ \/ / / / _ | / _ \/ //_/ / / / __/
 -/ /_/ / /_/ / __ |/ , _/ ,< / /_/ /\ \
--\___\_\____/_/ |_/_/|_/_/|_|\____/___/
2024-01-23 08:49:05,586 INFO  [io.und.servlet] (main) Initializing AtmosphereFramework
...
...
2024-01-23 08:49:05,666 INFO  [com.vaa.flo.ser.DefaultDeploymentConfiguration] (main) Vaadin is running in production mode.
2024-01-23 08:49:05,912 INFO  [org.apa.cam.qua.cor.CamelBootstrapRecorder] (main) Bootstrap runtime: org.apache.camel.quarkus.main.CamelMainRuntime
2024-01-23 08:49:05,913 INFO  [org.apa.cam.mai.MainSupport] (main) Apache Camel (Main) 4.2.0 is starting
...
...
2024-01-23 08:49:06,029 INFO  [com.tfy.bam.cam.CamelController] (main) configured
2024-01-23 08:49:06,074 INFO  [org.apa.cam.imp.eng.AbstractCamelContext] (main) Apache Camel 4.2.0 (camel-1) is starting
2024-01-23 08:49:06,081 INFO  [org.apa.cam.imp.eng.AbstractCamelContext] (main) Routes startup (total:10 started:0 disabled:10)
...
...
2024-01-23 08:49:06,085 INFO  [org.apa.cam.imp.eng.AbstractCamelContext] (main) Apache Camel 4.2.0 (camel-1) started in 10ms (build:0ms init:0ms start:10ms)
2024-01-23 08:49:06,193 INFO  [io.quarkus] (main) bambu-web 1.0.1 on JVM (powered by Quarkus 3.6.6) started in 1.421s. Listening on: http://0.0.0.0:8084
2024-01-23 08:49:06,194 INFO  [io.quarkus] (main) Profile prod activated.
2024-01-23 08:49:06,194 INFO  [io.quarkus] (main) Installed features: [camel-core, camel-direct, camel-paho, cdi, resteasy-reactive, resteasy-reactive-jackson, 
scheduler, security, servlet, smallrye-context-propagation, vaadin-quarkus, vertx, websockets, websockets-client]
```
* If starting correctly, it will show `Routes startup (total:10 started:0 disabled:10)` with a number that is 2x your printer count
* Head over to http://127.0.0.1:8080 and log in with `admin` / `admin`

# Building & Running

Building:
```bash
mvn clean install -Pproduction
```

Create a new directory and copy `bambu/target/bambu-web-1.0.0-runner.jar` into it, example:
```bash
tfyre@fsteyn-pc:/mnt/c/bambu-farm$ ls -al
total 64264
drwxrwxrwx 1 tfyre tfyre     4096 Jan 17 16:47 .
drwxrwxrwx 1 tfyre tfyre     4096 Jan 18 20:42 ..
-rw-rw-rw- 1 tfyre tfyre     4557 Jan 18 14:01 .env
-rw-rw-rw- 1 tfyre tfyre 65796193 Jan 18 20:38 bambu-web-1.0.0-runner.jar
```

Running
```bash
java -jar bambu-web-1.0.0-runner.jar
```

You can now access it via http://127.0.0.1:8080 (username: admin / password: admin)

# Running as a service

Refer to [README.service.md](/docs/README.service.md)

# Example Config

## Minimal config

**!!Remeber to replace `REPLACE_*` fields!!**

Create an `.env` file with  the following config:
```properties
quarkus.http.host=0.0.0.0
quarkus.http.port=8080

bambu.printers.myprinter1.device-id=REPLACE_WITH_DEVICE_SERIAL
bambu.printers.myprinter1.access-code=REPLACE_WITH_DEVICE_ACCESSCODE
bambu.printers.myprinter1.ip=REPLACE_WITH_DEVICE_IP

bambu.users.admin.password=admin
bambu.users.admin.role=admin
```

## Full Config Options

**All default options are displayed (only add to the config if you want to change)**

### Dark Mode
```properties
# Gobal
bambu.dark-mode=false
# Per user (will default to global if omitted)
bambu.users.myUserName.dark-mode=false
```

### Printer section
```properties
bambu.printers.myprinter1.enabled=true
bambu.printers.myprinter1.name=Name With Spaces
bambu.printers.myprinter1.device-id=REPLACE_WITH_DEVICE_SERIAL
bambu.printers.myprinter1.username=bblp
bambu.printers.myprinter1.access-code=REPLACE_WITH_DEVICE_ACCESSCODE
bambu.printers.myprinter1.ip=REPLACE_WITH_DEVICE_IP
bambu.printers.myprinter1.use-ams=true
bambu.printers.myprinter1.timelapse=true
bambu.printers.myprinter1.bed-levelling=true
bambu.printers.myprinter1.flow-calibration=true
bambu.printers.myprinter1.vibration-calibration=true
bambu.printers.myprinter1.model=unknown / a1 / a1mini / p1p / p1s / x1c
bambu.printers.myprinter1.mqtt.port=8883
bambu.printers.myprinter1.mqtt.url=ssl://${bambu.printers.myprinter1.ip}:${bambu.printers.myprinter1.mqtt.port}
bambu.printers.myprinter1.mqtt.report-topic=device/${bambu.printers.myprinter1.device-id}/report
bambu.printers.myprinter1.mqtt.request-topic=device/${bambu.printers.myprinter1.device-id}/request
#Requesting full status interval
bambu.printers.myprinter1.mqtt.full-status=10m
bambu.printers.myprinter1.ftp.port=990
bambu.printers.myprinter1.ftp.url=ftps://${bambu.printers.myprinter1.ip}:${bambu.printers.myprinter1.ftp.port}
bambu.printers.myprinter1.ftp.log-commands=false
bambu.printers.myprinter1.stream.port=6000
bambu.printers.myprinter1.stream.live-view=false
bambu.printers.myprinter1.stream.url=ssl://${bambu.printers.myprinter1.ip}:${bambu.printers.myprinter1.stream.port}
#Restart stream if no images received interval
bambu.printers.myprinter1.stream.watch-dog=5m
```

### Cloud Section

Enable MQTT connection via cloud instead of directly to printer. 

The access userid and token can be fetched from your browser cookies or a multi liner curl
```bash
export MY_USERNAME=fixme@fixme.com
export MY_PASSWORD=fixme

# Request verification code
curl -sS --fail -X POST -H 'Content-Type: application/json' -d "{\"account\":\"${MY_USERNAME}\",\"password\":\"${MY_PASSWORD}\"}" https://api.bambulab.com/v1/user-service/user/login | jq
```

Output:
```json
{
  "accessToken": "",
  "refreshToken": "",
  "expiresIn": 0,
  "refreshExpiresIn": 0,
  "tfaKey": "",
  "accessMethod": "",
  "loginType": "verifyCode"
}
```

```bash
# Check email for verification code
export MY_CODE=1234
curl -sS --fail -X POST -H 'Content-Type: application/json' -d "{\"account\":\"${MY_USERNAME}\",\"code\":\"${MY_CODE}\"}" https://api.bambulab.com/v1/user-service/user/login | jq
```

Output:
```json
{
  "accessToken": "AA...",
  "refreshToken": "SAME_AS_ACCESS_TOKEN",
  "expiresIn": 7776000,
  "refreshExpiresIn": 7776000,
  "tfaKey": "",
  "accessMethod": "",
  "loginType": ""
}
```

```bash
# Grab the access Token
export MY_TOKEN=AA...

# Grab username (uid) from here
curl -sS --fail  -H "Authorization: Bearer ${MY_TOKEN}" https://api.bambulab.com/v1/design-user-service/my/preference | jq '{"username": ("u_" + (.uid | tostring))}'
```

Output:
```json
{
  "username": "u_12345"
}
```

Configuration:

```properties
bambu.cloud.enabled=true
bambu.cloud.username=u_12345
bambu.cloud.token=AA...
```

### User Section

**Remember to encrypt your passwords with bcrypt (eg https://bcrypt-generator.com/)**

Current roles supported:

* `admin` - full access
* `normal` - only dashboard with readonly access

```properties
# https://bcrypt-generator.com/
#bambu.users.REPLACE_WITH_USERNAME.password=REPLACE_WITH_PASSWORD

# Insecure version:
#bambu.users.myUserName.password=myPassword
# Secure version:
bambu.users.myUserName.password=$2a$12$GtP15HEGIhqNdeKh2tFguOAg92B3cPdCh91rj7hklM7aSOuTMh1DC 
bambu.users.myUserName.role=admin
bambu.users.myUserName.dark-mode=false

#Guest account with readonly role
bambu.users.guest.password=guest
bambu.users.guest.role=normal

# Skip users and automatically login as admin (default: false)
bambu.auto-login=true
```

### Batch Print Section
Default batch printing options is below:

```properties
bambu.batch-print.skip-same-size=true
bambu.batch-print.timelapse=true
bambu.batch-print.bed-levelling=true
bambu.batch-print.flow-calibration=true
bambu.batch-print.vibration-calibration=true
bambu.batch-print.enforce-filament-mapping=true
```

### Preheat

Default preheat configuration is below:
```properties
bambu.preheat[0].name=Off 0/0
bambu.preheat[0].bed=0
bambu.preheat[0].nozzle=0
bambu.preheat[1].name=PLA 55/220
bambu.preheat[1].bed=55
bambu.preheat[1].nozzle=220
bambu.preheat[2].name=ABS 90/270
bambu.preheat[2].bed=90
bambu.preheat[2].nozzle=270
```

### Remote View

Remote View is the ability to remotely view or stream the printer's camera.

```properties
# defaults to true, when false, disables remote view globally
bambu.remote-view=true

# defaults to true, when false, disables remote view for dashboard, but will still be available in detail view
bambu.dashboard.remote-view=true

# defaults to true, when false, disables per printer
bambu.printers.myprinter1.stream.enable=true
```


### Live View

Live View is the ability to remotely stream the X1C camera (or any other webcam) and requires Remote View to be enabled.

> [!NOTE]
> Getting the **LiveView** to work requires additional software. For more details check the [docker/bambu-liveview](docker/bambu-liveview) README.


```properties
bambu.live-view-url=/_camerastream/

# For each printer:
bambu.printers.PRINTER_ID.stream.live-view=true

# Default LiveView URL
bambu.printers.PRINTER_ID.stream.url=${bambu.live-view-url}${PRINTER_ID}

# Custom LiveView URL
bambu.printers.PRINTER_ID.stream.url=https://my_stream_domain.com/mystream
# 
```


### Bouncy Castle
`X1C` needs SSL Session Reuse so that SD Card functionality can work. Reference: https://stackoverflow.com/a/77587106/23289205

Without this you will see `552 SSL connection failed: session resuse required`.

Add to `.env`:
```properties
bambu.use-bouncy-castle=true
```
Add JVM startup flag:

bash / cmd:
```bash
java -Djdk.tls.useExtendedMasterSecret=false -jar bambu-web-x.x.x-runner.jar
```

powershell:
```powershell
java "-Djdk.tls.useExtendedMasterSecret=false" -jar bambu-web-x.x.x-runner.jar
```

### Uploading bigger files

Add to `.env`:
```properties
quarkus.http.limits.max-body-size=30M
```

### Configure XY/Z movement speeds

Add to `.env`:
```properties
# values are in mm/minute
bambu.move-xy=5000
bambu.move-z=3000
```

### Use Right click for menus

Add to `.env`:
```properties
bambu.menu-left-click=false
```

### Display Filament Type instead of Name
Add to `.env`:
```properties
bambu.dashboard.filament-full-name=false
```



### Custom CSS

If you want to modify the CSS, create a file next to the `.jar` file called `styles.css`

#### Changing the display columns

*Display columns is a ratio and scale based on screen width*

Refer to [bambu.css](/bambu/frontend/themes/bambu-theme/bambu.css#L1-L25)

| Example | value for XXX |
| -- | -- |
| always 1 column | 1 |
| 2 columns with 1080p | 3 |
| 4 columns with 1080p | 5 |

```css
:root {
  --bambu-default-columns: XXX;
}
```


#### Ordering items inside printer box

* Move display order of `image` / `status` / `filaments` **"down"** so that `progress` is after `name`

```css
.dashboard-printer .image {
    order: 3;
}
.dashboard-printer .status {
    order: 4;
}
.dashboard-printer .filaments {
    order: 1;
}
```

# Debug

For debugging the application, add the following to .env and uncomment DEBUG or TRACE logging sections

```properties
### Log To File
quarkus.log.file.enable=true
quarkus.log.file.path=application.log


### DEBUG logging
#quarkus.log.category."com.tfyre".level=DEBUG


### TRACE logging
#quarkus.log.min-level=TRACE
#quarkus.log.category."com.tfyre".min-level=TRACE
#quarkus.log.category."com.tfyre".level=TRACE
```

# Links

## Inspirational Web interface

* https://github.com/davglass/bambu-farm/tree/main

## Printer MQTT Interface

* https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md
* https://github.com/xperiments-in/xtouch/blob/main/src/xtouch/device.h
* https://github.com/SoftFever/OrcaSlicer/blob/main/src/slic3r/GUI/DeviceManager.hpp

## Remoteview

* https://github.com/bambulab/BambuStudio/issues/1536#issuecomment-1811916472


## Images from

* https://github.com/SoftFever/OrcaSlicer/tree/main/resources/images

## Json to Proto

* https://json-to-proto.github.io/
* https://formatter.org/protobuf-formatter
