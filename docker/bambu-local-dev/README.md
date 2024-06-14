# Certs
password: 1234
```bash
openssl genrsa -des3 -out ca.key 2048
openssl req -new -x509 -days 1826 -key ca.key -out ca.crt
openssl genrsa -out server.key 2048
openssl req -new -out server.csr -key server.key
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out server.crt -days 3600
```
# Docker
```bash
docker compose up
```

# Testing FTP
```bash
lftp ftps://test2:test2@localhost
debug
set ssl:verify-certificate false
ls
```
