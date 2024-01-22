# Bambu Farm
[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/tfyre)

Web based application to monitor multiple bambu printers using mqtt / ftp / rtsp (**no custom firmware required**)

Technologies used:
* Java 21 https://www.azul.com/
* Quarkus https://quarkus.io/
* Vaadin https://vaadin.com/

# Features / Supported Devices

| Feature | A1 | A1 Mini | P1P | P1S | X1C|
|--|:--:|:--:|:--:|:--:|:--:|
|**Remote View**|<ul><li>[x] </li></ul>|?|<ul><li>[x] </li></ul>|<ul><li>[x] </li></ul>|?
|**Upload to SD card**|?|?|<ul><li>[x] </li></ul>|<ul><li>[x] </li></ul>|?
|**Print .3mf from SD card**|?|?|<ul><li>[x] </li></ul>|<ul><li>[x] </li></ul>|?
|**Print .gcode from SD card**|?|?|?|?|?
|**AMS**|?|?|?|<ul><li>[x] </li></ul>|?
|**Send Custom GCode**|?|?|?|<ul><li>[x] </li></ul>|?

**Currently only .3mf sliced projects are supported.**

> In Bambu Studio/Orca slicer, make sure to slice the place and then use the "File -> Export -> Export plate sliced file". This creates a `.3mf` project with embedded `.gcode` plate.

# Screenshots

![Desktop browser](/docs/bambufarm1.jpg)

*More screenshots in [docs](/docs)*

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

# Example Config

## Minimal config

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

**All default options are displayed**

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
bambu.printers.myprinter1.stream.url=ssl://${bambu.printers.myprinter1.ip}:${bambu.printers.myprinter1.stream.port}
#Restart stream if no images received interval
bambu.printers.myprinter1.stream.watch-dog=5m
```

### User Section

**Remember to encrypt your passwords with bcrypt (eg https://bcrypt-generator.com/)**

Current roles supported:

* `admin` - full access
* `normal` - only dashboard with readonly access

```properties
#https://bcrypt-generator.com/
#bambu.users.REPLACE_WITH_USERNAME.password=REPLACE_WITH_PASSWORD

#Insecure version:
#bambu.users.myUserName.password=myPassword
#Secure version:
bambu.users.myUserName.password=$2a$12$GtP15HEGIhqNdeKh2tFguOAg92B3cPdCh91rj7hklM7aSOuTMh1DC 
bambu.users.myUserName.role=admin
bambu.users.myUserName.dark-mode=false

#Guest account with readonly role
bambu.users.guest.password=guest
bambu.users.guest.role=normal
```

### Custom CSS

If you want to modify the CSS, create a file next to the `.jar` file called `styles.css`

```css
/* Add your custom CSS here */

/*Setting 2 display columns on ~1920x1080 display*/
.dashboard-printer .image img {
    max-height: 507px !important;
    max-width: 900px !important;
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

# TODO

* Move Axis
* Colour Mapping when printing from frontend

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
