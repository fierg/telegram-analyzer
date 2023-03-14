#!/bin/sh

DMP_PATH=../dumps/dump_`date +%d-%m-%Y"_"%H_%M_%S`.sql

echo Pulling dump from database... Please wait

mkdir -p ../dumps
docker exec -t querscraper_db pg_dumpall -c -U querscraper_admin > $DMP_PATH

echo Dump written to $DMP_PATH
