#!/bin/bash
set -eu
BUILD_DIR="$(mktemp -d "${TMPDIR:-/tmp}/magcalc-project-tests.XXXXXX")"
trap 'rm -rf "$BUILD_DIR"' EXIT
swiftc MAGCalc/MAGCalculator.swift MAGCalc/VesselProjects.swift Tests/main.swift -o "$BUILD_DIR/tests"
"$BUILD_DIR/tests"
