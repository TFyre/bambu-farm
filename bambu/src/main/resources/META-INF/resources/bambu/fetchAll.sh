#!/bin/bash
set -eu
PREFIX=https://raw.githubusercontent.com/SoftFever/OrcaSlicer/main/resources/images/


fetch() {
  FILE=${1}
  echo Fetching ${FILE}
  curl -Ss --fail -o ${FILE} ${PREFIX}${FILE}
}

fetch monitor_lamp_off.svg
fetch monitor_lamp_on.svg
fetch monitor_bed_temp.svg
fetch monitor_bed_temp_active.svg
fetch monitor_nozzle_temp.svg
fetch monitor_nozzle_temp_active.svg
fetch monitor_frame_temp.svg
fetch monitor_speed.svg
fetch monitor_speed_active.svg
fetch ams_humidity_0.svg
fetch ams_humidity_1.svg
fetch ams_humidity_2.svg
fetch ams_humidity_3.svg
fetch ams_humidity_4.svg
