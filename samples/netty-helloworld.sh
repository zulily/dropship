#!/bin/sh
(cd `dirname "$0"`;
 echo "*****************************************************";
 echo "** Using dropship to run a netty 'hello world' server";
 echo "** curl http://localhost:8080 to try it out!";
 echo "*****************************************************\n";
 sleep 3
 java $OPTS -Dverbose -jar ../target/dropship-*.jar io.netty:netty-example:4.0.17.Final io.netty.example.http.helloworld.HttpHelloWorldServer;
)
