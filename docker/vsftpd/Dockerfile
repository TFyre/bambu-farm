#Copied from https://github.com/fauria/docker-vsftpd/blob/master/run-vsftpd.sh
FROM centos:7

ARG USER_ID=14
ARG GROUP_ID=50

RUN yum -y update \
  && yum clean all \
  && yum install -y vsftpd db4-utils db4 iproute \
  && yum clean all

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