#!/bin/sh
(cd `dirname "$0"`;
 echo "Using dropship to run cassandra";
 java -Dverbose -jar ../target/dropship-*.jar org.apache.cassandra:cassandra-all org.apache.cassandra.service.CassandraDaemon $*;
 )
