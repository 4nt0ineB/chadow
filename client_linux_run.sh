#!/bin/bash


# get login or random
random_int=$(( (RANDOM % 100) + 1 ))
login=${1:-"Client$random_int"}

# get tty size
read x y <<< $(stty size)
# Run chadow Client
cmd="java -jar --enable-preview target/chadow-1.0.0.jar $login localhost 7777 $((x - 10)) $y 2>logs"
echo $cmd >> logs
java -jar --enable-preview target/chadow-1.0.0.jar $login localhost 7777 $((x - 10)) $y 2>logs