#!/bin/sh
(cd `dirname "$0"`;
 echo "Using dropship to run cassandra";
 java -Ddropship.additional.paths=. -jar ../target/dropship-*.jar org.apache.cassandra:cassandra-all:1.2.15 org.apache.cassandra.service.CassandraDaemon $*;
 )
