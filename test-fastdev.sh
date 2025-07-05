#!/bin/bash

# Script to test the fastDev task and check if protobuf generation is skipped

echo "Running fastDev task to test protobuf generation skipping..."
cd /home/krickert/IdeaProjects/rokkon/rokkon-pristine

# Run the fastDev task and capture the output
./gradlew :engine:pipestream:fastDev | tee fastdev-output.log &

# Store the PID of the background process
PID=$!

echo "fastDev task is running in the background with PID $PID"
echo "Check the fastdev-output.log file for output"
echo "To stop the process, run: kill $PID"
echo "To check if protobuf generation was skipped, run: grep -i 'proto generation will be skipped' fastdev-output.log"