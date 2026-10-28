#!/bin/bash
# Sends each line of the given file as a console command, then prints the new journal lines.
C=/home/mcserver/mcserver.control
since=$(date '+%Y-%m-%d %H:%M:%S')
while IFS= read -r cmd || [ -n "$cmd" ]; do
  cmd=${cmd%$'\r'}
  [ -z "$cmd" ] && continue
  if [[ $cmd == sleep* ]]; then $cmd; continue; fi
  echo "$cmd" > "$C"
  sleep "${MC_DELAY:-0.3}"
done < "$1"
sleep 1
journalctl -u mcserver --since "$since" --no-pager -o cat | grep -v '^\s*at '
