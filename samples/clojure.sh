#!/bin/sh
(cd `dirname "$0"`;
 echo "Using dropship to run clojure cli";
 java $OPTS -Drepo.local.path=/tmp/.m2 -jar ../target/dropship-*.jar org.clojure:clojure clojure.main $*;
 )
