# Bambu Web
Web based application to monitor multiple bambu printers using mqtt 

Technologies used:
* Java 21 https://www.azul.com/
* Quarkus https://quarkus.io/
* Vaadin https://vaadin.com/

# Links

## Printer Interface

* https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md
* https://github.com/xperiments-in/xtouch/blob/main/src/xtouch/device.h
* https://github.com/SoftFever/OrcaSlicer/blob/main/src/slic3r/GUI/DeviceManager.hpp

Stream
* https://github.com/hisptoot/BambuP1PSource2Raw
* https://github.com/bambulab/BambuStudio/issues/1536#issuecomment-1812001751

## Inspirational Web interface

* https://github.com/davglass/bambu-farm/tree/main

## Images from

* https://github.com/SoftFever/OrcaSlicer/tree/main/resources/images

## Json to Proto

* https://json-to-proto.github.io/
* https://formatter.org/protobuf-formatter


# Building & Running

Building:
```
mvn clean install -Pproduction
```

Create a new directory and copy `bambu/target/bambu-web-1.0.0-runner.jar` into it:
```
tfyre@fsteyn-pc:/mnt/c/bambu-farm$ ls -al
total 64264
drwxrwxrwx 1 tfyre tfyre     4096 Jan 17 16:47 .
drwxrwxrwx 1 tfyre tfyre     4096 Jan 18 20:42 ..
-rw-rw-rw- 1 tfyre tfyre     4557 Jan 18 14:01 .env
-rw-rw-rw- 1 tfyre tfyre 65796193 Jan 18 20:38 bambu-web-1.0.0-runner.jar
```

Running
```
java -jar bambu-web-1.0.0-runner.jar
```

# Example Config

Create an `.env` file with  the following config:

```
quarkus.http.host=0.0.0.0
quarkus.http.port=1234 FIXME

### DEBUG logging
#quarkus.log.category."com.tfyre".level=DEBUG

### TRACE logging
#quarkus.log.min-level=TRACE
#quarkus.log.category."com.tfyre".min-level=TRACE
#quarkus.log.category."com.tfyre".level=TRACE


#bambu.printers."My Template".enabled=false
bambu.printers."My Template".device-id=FIXME
#bambu.printers."My Template".username=bblp
bambu.printers."My Template".access-code=FIXME
bambu.printers."My Template".ftp.url=ftps://FIXME:990
#has default value bambu.printers."My Template".ftp.thumbnail=/timelapse/thumbnail/
#bambu.printers."My Template".ftp.log-commands=false
bambu.printers."My Template".mqtt.url=ssl://FIXME:8883
bambu.printers."My Template".mqtt.report-topic=device/${bambu.printers."My Template".device-id}/report
bambu.printers."My Template".mqtt.request-topic=device/${bambu.printers."My Template".device-id}/request

#https://bcrypt-generator.com/
#bambu.users."My UserName".password=insecure
bambu.users."My UserName".password=$2a$12$zTZT5yfKa/V2uMi/KbGQuutqgcHKdqcjIO1Nsj74oAgC1YpEdJwKK
bambu.users."My UserName".role=admin
bambu.users.guest.password=guest
bambu.users.guest.role=normal

```