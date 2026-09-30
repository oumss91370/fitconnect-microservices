#!/usr/bin/env bash
# Lance les 7 applications en local (après "mvn clean package -DskipTests"), logs dans ./logs
set -e
cd "$(dirname "$0")"
mkdir -p logs
JAVA_OPTS="${JAVA_OPTS:--Xms64m -Xmx256m}"
start() { echo "-> $1"; nohup java $JAVA_OPTS -jar "$1/target/$1.jar" > "logs/$1.log" 2>&1 & echo $! > "logs/$1.pid"; }
wait_up() { for i in $(seq 1 90); do curl -sf "$1" > /dev/null && return 0; sleep 2; done; echo "timeout $1"; return 1; }

start eureka-server;  wait_up http://localhost:8761/actuator/health
start config-server;  wait_up http://localhost:8888/actuator/health
for s in class-service payment-service notification-service booking-service; do start $s; done
wait_up http://localhost:8091/actuator/health; wait_up http://localhost:8092/actuator/health
wait_up http://localhost:8093/actuator/health; wait_up http://localhost:8094/actuator/health
start api-gateway;    wait_up http://localhost:8080/actuator/health
echo "Stack démarrée : gateway http://localhost:8080 - Eureka http://localhost:8761"
