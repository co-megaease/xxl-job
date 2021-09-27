FROM megaease/easeimg-javabuild:latest AS builder

ARG      REPOSITORY1
ARG      REPOSITORY2
ARG      REPOSITORY3
ARG      MIRROR1
ARG      MIRROR2
ARG      MIRROR3
ARG      SERVER1
ARG      SERVER2
ARG      SERVER3

COPY     pom.xml /xxl-job-admin/pom.xml
COPY     src /xxl-job-admin/src
COPY     resources /xxl-job-admin/resources

WORKDIR  /xxl-job-admin

RUN 	 /bin/rewrite-settings.sh && cd /xxl-job-admin/ && mvn dependency:go-offline -B

RUN      mkdir out \
         && /xxl-job-admin/resources/scripts/build-app.sh

FROM openjdk:8u171-jdk-slim

RUN mkdir /xxl-job-admin

COPY --from=builder /xxl-job-admin/target/xxl-job-admin-1.0-SNAPSHOT.jar /xxl-job-admin
COPY --from=builder /xxl-job-admin/resources/scripts/bootstrap.sh  /

WORKDIR /xxl-job-admin

EXPOSE 18083

ENV PARAMS=""

ENV TZ=PRC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

CMD [ "/bootstrap.sh" ]
