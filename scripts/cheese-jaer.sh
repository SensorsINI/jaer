#!/usr/bin/env bash
# Cheese 41 on Ubuntu uses PipeWire (pipewiresrc). That path cannot convert a
# v4l2loopback that only exposes one format (MJPEG or YUYV), so preview fails
# with not-negotiated. This launches Cheese with the PipeWire GStreamer plugin
# omitted so it uses v4l2src.
#
# Do not pass --device=/dev/video10: Cheese matches the GSettings camera
# string to the GstDevice name ("jAER") or path. A path miss selects the
# first camera (Logitech). An already-running Cheese also ignores --device.
set -eu
GSTDIR=/usr/lib/x86_64-linux-gnu/gstreamer-1.0
if [[ ! -d "$GSTDIR" ]]; then
  GSTDIR="/usr/lib/$(uname -m)-linux-gnu/gstreamer-1.0"
fi
TMP=$(mktemp -d /tmp/gst-no-pipewire.XXXXXX)
trap 'rm -rf "$TMP"' EXIT
for f in "$GSTDIR"/*.so; do
  b=$(basename "$f")
  [[ "$b" == *pipewire* ]] && continue
  ln -s "$f" "$TMP/$b"
done
export GST_PLUGIN_SYSTEM_PATH_1_0="$TMP"
export GST_REGISTRY_1_0="$TMP/registry.bin"
if pgrep -x cheese >/dev/null; then
  killall -q cheese || true
  sleep 0.4
fi
gsettings set org.gnome.Cheese camera 'jAER'
gsettings set org.gnome.Cheese photo-x-resolution 640
gsettings set org.gnome.Cheese photo-y-resolution 480
gsettings set org.gnome.Cheese video-x-resolution 640
gsettings set org.gnome.Cheese video-y-resolution 480
exec cheese "$@"
