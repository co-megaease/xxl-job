#!/bin/bash

set -e

pushd $(dirname $0) > /dev/null
SCRIPTPATH=$(pwd -P)
popd > /dev/null

do_package() {
  cd /xxl-job/
  mvn package -Dmaven.test.skip=true
}

do_package
