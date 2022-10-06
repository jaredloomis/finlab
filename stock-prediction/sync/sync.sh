#!/bin/sh

START_DIR="$(pwd)"
cd "$(dirname $0)"
pipenv run python sync.py
cd "$START_DIR"
