#!/bin/bash

set -meu

# run background_job_gpid test_command
run () {
    PID=$1
    shift
    CODE=0
    "$@" || CODE=$?
    kill -- -$PID || true
    sleep 1
    return $CODE
}

echo "*** Testing Sleep Benchmark ***"

./knative-quarkus-bench/benchmarks/sleep/target/native-sleep-1.0.0-SNAPSHOT/sleep-1.0.0-SNAPSHOT-runner &
run $! curl --retry-connrefused --max-time 120 --retry 10 --retry-delay 3 -s -w "\n" -H 'Content-Type:application/json' -d '"test"' -X POST http://$QUARKUS_HTTP_HOST:$QUARKUS_HTTP_PORT/sleep
