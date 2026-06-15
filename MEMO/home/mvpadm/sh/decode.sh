#!/usr/bin/env bash
echo $1 | openssl aes-256-cbc -d -a -pass pass:b77a5c561934e089
