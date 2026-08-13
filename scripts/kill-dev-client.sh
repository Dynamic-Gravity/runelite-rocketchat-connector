#!/usr/bin/env bash
# Kills leftover RuneLite dev-client JVMs started by `./gradlew run`.
#
# `./gradlew run` forks a JavaExec running space.covalent.rocketchat.RocketChatConnectorPluginTest
# (see build.gradle's pluginMainClass), not a process literally named "gradlew run" or "java run" —
# so `pgrep gradlew` won't find it. This matches on the actual main class instead.
set -euo pipefail

MAIN_CLASS="space.covalent.rocketchat.RocketChatConnectorPluginTest"

pids=$(pgrep -f "$MAIN_CLASS" || true)

if [[ -z "$pids" ]]; then
	echo "No running dev-client JVMs found."
	exit 0
fi

echo "Found dev-client JVM(s): $pids"
kill $pids

for _ in $(seq 1 5); do
	sleep 1
	pids=$(pgrep -f "$MAIN_CLASS" || true)
	[[ -z "$pids" ]] && { echo "Terminated cleanly."; exit 0; }
done

echo "Still alive after SIGTERM, forcing: $pids"
kill -9 $pids
