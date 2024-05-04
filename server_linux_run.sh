#!/bin/sh

mkdir -p logs

java -jar --enable-preview target/chadow-1.0.0.jar --server --log:true