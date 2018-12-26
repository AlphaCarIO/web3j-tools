#!/bin/bash

#$1 configfile
#$2 transfer list
#$3 t transfer flag

set -x

python scripts/fetch_gas.py

gasprice=`cat gas_price`

echo 'gasprice='$gasprice

if [ x'' == x$gasprice ]; then
    gasprice=3
    echo "set gas price to default value ($gasprice)"
fi

java -cp 'target/web3j-tools-1.0.jar:target/lib/*' com.alphacar.ethtools.TokenTransfer -c $1 -f $2 -g $gasprice $3 $4
