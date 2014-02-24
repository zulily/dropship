#!/bin/sh
die () {
  echo >&2 "$@"
  exit 1
}

[ "$#" -eq 1 ] || die "please specify a war file to run"

(cd `dirname "$0"`;
 echo "Using dropship to run $1 in Jetty";
 java $OPTS -Dverbose -jar ../target/dropship-*.jar org.mortbay.jetty:jetty-runner org.mortbay.jetty.runner.Runner $1;
 )
