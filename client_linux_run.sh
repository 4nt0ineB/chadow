#!/bin/bash


############ CHANGE ME

hostname=localhost
port=7777
log=false
debug=false

############

# Create logs directory
mkdir -p logs

if [ -z "$1" ]; then
    login=""
else
    login="--login:$1"
fi

# get tty size
# shellcheck disable=SC2046
read -r y x <<< $(stty size)
echo "$login"
# Run chadow Client
java -jar --enable-preview target/chadow-1.0.0.jar "$login" --hostname:$hostname --port:$port --y:$((y - 4)) --x:"$x" --log:$log --debug:$debug 2>logs/global.log