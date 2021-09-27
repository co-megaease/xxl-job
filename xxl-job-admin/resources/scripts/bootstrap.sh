#!/bin/bash

SERVICE_NAME=iot-xxl-job-admin

trace_gc() {
  local meta_full_gc_size

  meta_full_gc_size=$1

  JAVA_OPTS="${JAVA_OPTS} \
    -XX:+UseGCLogFileRotation \
    -XX:NumberOfGCLogFiles=30 \
    -XX:GCLogFileSize=4096K \
    -XX:+ParallelRefProcEnabled \
    -XX:+PrintGCTimeStamps \
    -XX:+PrintGCDetails \
    -XX:+PrintTenuringDistribution \
    -XX:MetaspaceSize=${meta_full_gc_size} \
    -Xloggc:/tmp/gc.log \
    -noverify"
}

chose_stage() {
  local stage=$1
  JAVA_OPTS="${JAVA_OPTS} -Dspring.profiles.active=${stage}"
}

jmx() {
   local jmx_port=$1
   JAVA_OPTS="${JAVA_OPTS} \
    -Dcom.sun.management.jmxremote \
    -Dcom.sun.management.jmxremote.port=${jmx_port}  \
    -Dcom.sun.management.jmxremote.authenticate=false  \
    -Dcom.sun.management.jmxremote.local.only=false  \
    -Dcom.sun.management.jmxremote.ssl=false"
}

jdwp(){
    local jdwp_bind_address=$1
    JAVA_OPTS="${JAVA_OPTS} \
    -Xdebug \
    -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=${jdwp_bind_address}"
}

memory() {
  local  min_heap_size=$1
  local  max_heap_size=$2

  local current
  current=$(date +%y%m%d-%H%M%S)

  JAVA_OPTS="${JAVA_OPTS} -Xms${min_heap_size} \
    -Xmx${max_heap_size} \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/tmp/oom-error-${current}.hprof"
}

gc() {
  local region_size=$1
  JAVA_OPTS="${JAVA_OPTS} \
    -XX:+UseG1GC \
    -XX:+PrintAdaptiveSizePolicy \
    -XX:MaxGCPauseMillis=350 \
    -XX:G1HeapRegionSize=${region_size}M"
}

listen_port() {
  local port=$1
  local manager_port=$2

  JAVA_OPTS="${JAVA_OPTS} -Dserver.port=${port} -Dmanagement.port=${manager_port}"
}


JMX_PORT=38102
DEFAULT_STAGE=localhost
DEFAULT_REGION_SIZE=32
DEFAULT_HEAP_SIZE=1024m
DEFAULT_WORK_PORT=38100
DEFAULT_MNG_PORT=38101
DEFAULT_CONTAINER_MEMROY=3GB
DEFAULT_METASPACE_SIZE=256m
JDWP_BIND_ADDRESS=28101

show_usage() {
  printf "usage: ./startup.sh [-p port] [-j jxm_port] [-a profile] [-s heap_size] [-m management_port] [-c contianer_memory_size] [-h]\\n"
  printf "    -p port\\n"
  printf "        The listened port of the easepay system, the default is:38080\\n"
  printf "    -m management_port\\n"
  printf "        The listened management_port of the easepay system, the default is:38081\\n"
  printf "    -j jmx_port\\n"
  printf "        The jmx_port of the easepay system, the default is: 38082\\n"
  printf "    -a profile\\n"
  printf "        The profile at which the easepay is running, the default is: \"localhost\"\\n"
  printf "    -s heap_size\\n"
  printf "        The heap size of JVM, default is 1g. Because we use G1 garbage collection\\n"
  printf "        it works better with larger heap size, you should make this value as big\\n"
  printf "        as possible\\n"
  printf "    -c container_memory_size\\n"
  printf "        The limit memory size of the container by cgroup.\\n"
  printf "        The value should not smaller than heap_size, an empirical value\\n"
  printf "        is 1.2 times the size of heap_size, the default is 3GB\\n"
  printf "    -h show this messages\\n"
}


while getopts 'hp:c:a:s:m:j:r:' flag; do
  case "${flag}" in
    h) show_usage "$0"; exit ;;
    p) DEFAULT_WORK_PORT=${OPTARG};;
    a) DEFAULT_STAGE=${OPTARG};;
    s) DEFAULT_HEAP_SIZE=${OPTARG};;
    m) DEFAULT_MNG_PORT=${OPTARG};;
    c) DEFAULT_CONTAINER_MEMROY=${OPTARG};;
    j) jmx "${OPTARG}";;
    r) jdwp "${OPTARG}";;
    *) show_usage; exit ;;
  esac
done

#JAVA_OPTS=-Duser.timezone=Asia/Shanghai
JAVA_OPTS=

memory "${DEFAULT_HEAP_SIZE}" "${DEFAULT_HEAP_SIZE}"
gc "${DEFAULT_REGION_SIZE}"
# listen_port "${DEFAULT_WORK_PORT}" "${DEFAULT_MNG_PORT}"
trace_gc  "${DEFAULT_METASPACE_SIZE}"
chose_stage "${DEFAULT_STAGE}"


java -server ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -jar /xxl-job-admin/xxl-job-admin-1.0-SNAPSHOT.jar
