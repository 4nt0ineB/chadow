#!/bin/bash


# get login or random
random_int=$(( (RANDOM % 100) + 1 ))
login=${1:-"Client$random_int"}

# get tty size
read y x <<< $(stty size)
# Run chadow Client
java -jar --enable-preview target/chadow-1.0.0.jar --login:$login--hostname:localhost--port:7777--y:$((y - 4))--x:$x 2>dummy.log
