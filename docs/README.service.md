# Runnig as a linux service

All commands should be executed as `root`

Ensure you have Java 21 running
```bash
root@raspberrypi:~# java --version

openjdk 21.0.2 2024-01-16 LTS
OpenJDK Runtime Environment Zulu21.32+17-CA (build 21.0.2+13-LTS)
OpenJDK 64-Bit Server VM Zulu21.32+17-CA (build 21.0.2+13-LTS, mixed mode, sharing)
```

Create a new user & setup folder
```bash
export BAMBU_USER=bambu-web
#create the user without shell
useradd -m --shell=/bin/false ${BAMBU_USER}
#create service folder
mkdir -p /usr/local/${BAMBU_USER}
#setup permissions
chown ${BAMBU_USER}.${BAMBU_USER} /usr/local/${BAMBU_USER}
```

Download / Update the bambu-web version
```bash
export BAMBU_USER=bambu-web
#login as user
su ${BAMBU_USER} -s /bin/bash
#change to service folder
cd /usr/local/${BAMBU_USER}
#grab the latest version
export BAMBU_URL=$(curl -s https://api.github.com/repos/tfyre/bambu-farm/releases/latest \
  | grep browser_download_url | cut -d'"' -f4)
curl -LO ${BAMBU_URL}
ln -sf $(basename ${BAMBU_URL}) bambu-web-latest-runner.jar
exit
```

Create/edit the config file using your favorite editor (eg vim)
```bash
export BAMBU_USER=bambu-web
#login as user
su ${BAMBU_USER} -s /bin/bash
#change to service folder
cd /usr/local/${BAMBU_USER}
vim .env
```

Create & start the service
```bash
export BAMBU_USER=bambu-web
# Create the service file
cat <<EOF > /etc/systemd/system/${BAMBU_USER}.service
[Unit]
Description=Bambu Web Service
Requires=network.target remote-fs.target
After=network.target remote-fs.target

[Service]
Type=simple
User=${BAMBU_USER}
Group=${BAMBU_USER}
Restart=always
RestartSec=30
ExecStart=java -jar bambu-web-latest-runner.jar
WorkingDirectory=/usr/local/${BAMBU_USER}
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
EOF

#enable the service for system startup
systemctl enable ${BAMBU_USER}
#start the service
systemctl start ${BAMBU_USER}
#check the status
systemctl status ${BAMBU_USER}
```

# Removing the service

```bash
export BAMBU_USER=bambu-web
#stop the service
systemctl stop ${BAMBU_USER}
#disable the service
systemctl disable ${BAMBU_USER}
#remove the service
rm -f /etc/systemd/system/${BAMBU_USER}.service

#remove the service folder
rm -fr /usr/local/${BAMBU_USER}
#remove the user
userdel -r ${BAMBU_USER}
```

