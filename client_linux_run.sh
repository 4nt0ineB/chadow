#!/bin/bash


# get login or random
random_int=$(( (RANDOM % 100) + 1 ))
login=${1:-"Client$random_int"}

# get tty size
read y x <<< $(stty size)
# Run chadow Client
cmd="java -jar --enable-preview target/chadow-1.0.0.jar --login:$login --hostname:localhost --port:7777 --x:$((x - 4)) --y:$y 2>logs"
echo $cmd >> logs
if [[ "$1" == "Alan1" ]]; then
    err="logs"
else
    err="lougs"
fi
java -jar --enable-preview target/chadow-1.0.0.jar --login:$login--hostname:localhost--port:7777--y:$((y - 4))--x:$x 2>$err