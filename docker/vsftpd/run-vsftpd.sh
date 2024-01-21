#!/bin/bash
/usr/bin/db_load -T -t hash -f /etc/vsftpd/virtual_users.txt /etc/vsftpd/virtual_users.db

&>/dev/null /usr/sbin/vsftpd /etc/vsftpd/vsftpd.conf