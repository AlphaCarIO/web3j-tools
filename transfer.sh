#!/bin/bash

#$1 configfile
#$2 transfer list
#$3 t transfer flag

java -cp 'target/web3j-tools-1.0.jar:target/lib/*' com.alphacar.ethtools.TokenTransfer $1 $2 $3
