#!/bin/bash
SCRIPT=$(readlink -f "$0")
SCRIPTPATH=$(dirname "$SCRIPT")
BOOTSTRAP="$SCRIPTPATH/../target/dropship-*.jar"

java $BOOTSTRAPOPTS $JAVA_OPTS -jar $BOOTSTRAP $*
