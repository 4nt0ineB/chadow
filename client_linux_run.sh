#!/bin/bash


############ CHANGE ME

hostname=217.182.68.48
port=7777
log=true
debug=false

############

mkdir -p "logs"
# get login or random
random_int=$(( (RANDOM % 100) + 1 ))
login=${1:-"Client$random_int"}
#
# get tty size
# shellcheck disable=SC2046
read y x <<< $(stty size)
# Run chadow Client
# localhost
# 217.182.68.48
java -jar --enable-preview target/chadow-1.0.0.jar --login:$login --hostname:$hostname --port:$port --y:$((y - 4)) --x:$x --log:true --debug:false 2>logs/"$login".log

