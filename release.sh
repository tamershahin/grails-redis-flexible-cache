#!/bin/bash

rm -rf target/release
mkdir target/release
cd target/release
git clone https://github.com/tamershahin/grails-redis-flexible-cache.git
cd grails-redis-flexible-cache
grails clean
grails compile
grails publish-plugin --stacktrace
