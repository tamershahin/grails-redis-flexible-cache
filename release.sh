#!/bin/bash

rm -rf target/release
mkdir target/release
cd target/release
git clone git@github.com:tamershahin/redis-flexible-cache.git
cd redis-flexible-cache
grails clean
grails compile
grails publish-plugin --stacktrace
