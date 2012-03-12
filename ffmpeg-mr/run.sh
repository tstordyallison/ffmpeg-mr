#!/bin/bash

# ---------------------------------
# A Very Rudimentary Test Script
# ---------------------------------

# Compile everything.
make

# Run FullTest
if [ -n $HADDOP_HOME ]; then
HADOOP_PATH=$HADOOP_HOME
else
HADOOP_PATH=$HOME/hadoop/hadoop-0.20.203.0
fi

JODA_PATH=lib/joda-time-2.0.jar
MRUNIT_PATH=lib/mrunit-0.8.0-incubating.jar
FMRCP=.:$HADOOP_PATH/*:$HADOOP_PATH/lib/*:$JODA_PATH:$MRUNIT_PATH
LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$HOME/build-ouptut/lib

java -cp $FMRCP -Xmx1024m -Djava.library.path=. com.tstordyallison.ffmpegmr.$1 $2 $3