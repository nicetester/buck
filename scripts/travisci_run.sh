#!/bin/sh
set -eux

# We export TERM=dumb to hide Buck's SuperConsole
# because otherwise Travis thinks our logs are too long.
export TERM=dumb

# Use Buck to run the tests first because its output is more
# conducive to diagnosing failed tests than Ant's is.
./bin/buck build buck || { cat buck-out/log/ant.log; exit 1; }

# There are only two cores in the containers, but Buck thinks there are 12
# and tries to use all of them.  As a result, we seem to have our test
# processes killed if we do not limit our threads.
export BUCK_NUM_THREADS=3

# Make sure that everything builds in case a library is not covered by a test.
./bin/buck build --num-threads=$BUCK_NUM_THREADS src/... test/...

# Until we get https://github.com/facebook/buck/pull/479, we cannot run the
# Go tests in Travis
./bin/buck test --num-threads=$BUCK_NUM_THREADS

# Run all the other checks with ant.
ant travis
