#Copied from https://github.com/fauria/docker-vsftpd/blob/master/run-vsftpd.sh
FROM quay.io/centos/centos:stream9

ARG USER_ID=14
ARG GROUP_ID=50

RUN dnf -y update \
  && dnf clean all \
  && dnf install -y vsftpd libdb-utils libdb iproute \
  && dnf clean all

RUN usermod -u ${USER_ID} ftp
RUN groupmod -g ${GROUP_ID} ftp

COPY vsftpd.conf /etc/vsftpd/
COPY vsftpd_virtual /etc/pam.d/
COPY run-vsftpd.sh /usr/sbin/

RUN chmod +x /usr/sbin/run-vsftpd.sh

VOLUME /home/vsftpd
VOLUME /var/log/vsftpd

#EXPOSE 990

CMD ["/usr/sbin/run-vsftpd.sh"]