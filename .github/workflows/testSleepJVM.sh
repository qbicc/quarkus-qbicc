#!/bin/bash

set -meu

# run background_job_gpid test_command
run () {
    PID=$1
    shift
    CODE=0
    date
    echo "executing command: $@"
    "$@" || CODE=$?
    date
    kill -- -$PID || true
    sleep 1
    return $CODE
}

echo "*** Testing Sleep Benchmark on JVM ***"

java -jar ./knative-quarkus-bench/benchmarks/sleep/target/quarkus-app/quarkus-run.jar &
run $! curl --retry-connrefused --max-time 60 --retry 10 --retry-delay 3 -s -w "\n" -H 'Content-Type:application/json' -d '"test"' -X POST http://$QUARKUS_HTTP_HOST:$QUARKUS_HTTP_PORT/sleep
