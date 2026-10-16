#!/usr/bin/env bash
# Boots the packaged mod jar (not the dev classpath) on a real NeoForge dedicated server with no
# soft dependency installed (quality gates, "Shipping artifact"). Fails unless the server loads
# vftv, finishes starting and stops cleanly, with no error from vftv.
set -euo pipefail
cd "$(dirname "$0")/.."

neo=$(sed -n 's/^neo_version=//p' gradle.properties)
mod=$(sed -n 's/^mod_version=//p' gradle.properties)
dir=build/packaged-server

./gradlew --console=plain jar
rm -rf "$dir"
mkdir -p "$dir/mods"
curl -fsSL -o "$dir/installer.jar" \
    "https://maven.neoforged.net/releases/net/neoforged/neoforge/$neo/neoforge-$neo-installer.jar"
(cd "$dir" && java -jar installer.jar --install-server . > installer.log 2>&1)
cp "build/libs/vftv-$mod.jar" "$dir/mods/"
echo "eula=true" > "$dir/eula.txt"
printf 'online-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\n' > "$dir/server.properties"

# Start the server with its console on a FIFO, wait for "Done", then type "stop".
log="$dir/server.log"
mkfifo "$dir/console"
(cd "$dir" && timeout 600 ./run.sh nogui < console > server.log 2>&1) &
pid=$!
exec 3> "$dir/console"
for _ in $(seq 1 540); do
    if grep -q "Done (" "$log" 2> /dev/null || ! kill -0 "$pid" 2> /dev/null; then
        break
    fi
    sleep 1
done
echo stop >&3
exec 3>&-
status=0
wait "$pid" || status=$?

fail() {
    echo "Packaged-jar boot failed: $1 (exit $status). See $log."
    tail -n 60 "$log"
    exit 1
}
[ "$status" -eq 0 ] || fail "the server did not exit cleanly"
grep -q "Done (" "$log" || fail "the server never finished starting"
grep -Eq "Voices From The Void $mod|vftv@$mod|\\(vftv\\)" "$log" || fail "vftv was not loaded"
if grep -E "/(ERROR|FATAL)\]" "$log" | grep -iq "vftv"; then
    fail "vftv logged an error"
fi
echo "The packaged vftv-$mod.jar booted on NeoForge $neo and stopped cleanly."
