#!/usr/bin/python
# coding=utf-8

import re
import requests
#from BeautifulSoup import BeautifulSoup
from bs4 import BeautifulSoup

import io
import sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf8')
import math
import os, sys

HEADERS = {
    # 模拟登陆的浏览器
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36'
}

def fetch_gas(url):
        res = requests.get(url, headers = HEADERS)
        res.encoding = 'utf-8'

        if res.status_code != 200:
            return "0"

        html = BeautifulSoup(res.text, 'html.parser')
        #divs = html.find_all('div', attrs = {'class':'col-md-2 col-sm-4 col-xs-6 tile_stats_count'})
        divs = html.find_all('div', attrs = {'class':'col-md-4 col-sm-4 col-xs-6 tile_stats_count'})

        for div in divs:
            print('div:', div)
            #
            ind = str(div).find('count fast')
            #ind = str(div).find('Gas Price Std (Gwei)')
            if ind != -1:
                print('------>find div', div)
                price = div.find('div', 'count')
                return str(int(math.ceil(float(price.text))))

        return "0"

if __name__ == "__main__":

    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("-c", "--config", help = "config file name", type = str, required = True)
    parser.add_argument("-f", "--file", help = "data file name", type = str, required = True)
    parser.add_argument("-t", "--transfer", help = "transfer flag", type = str, required = False)
    parser.add_argument("-u", "--update", help = "update flag", type = str, required = False)
    parser.parse_args()

    price = fetch_gas('https://ethgasstation.info/')

    print('fetch gas: ' + price)

    os.system("echo " + price + " > gas_price")

    os.system("java -cp 'target/web3j-tools-1.0.jar:target/lib/*' com.alphacar.ethtools.TokenTransfer -c %s -f %s -g %s %s %s"
              % (parser.config, parser.file, price, parser.transfer, parser.update))
