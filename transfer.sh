#!/bin/bash

#:./target/lib/
java -cp 'target/web3j-tools-1.0.jar:target/lib/*' com.alphacar.ethtools.TokenTransfer $1 $2 $3
