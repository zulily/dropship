#!/bin/sh
(cd `dirname "$0"`;
 echo "Using dropship to run clojure cli";
 java $OPTS -Dverbose -jar ../target/dropship-*.jar org.clojure:clojure clojure.main $*;
 )
