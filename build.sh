#!/bin/bash

cp config.js graphhopper-maps/

# Build GraphHopper Maps
# TODO: sync submodule?
cd graphhopper-maps
npm install
npm run build
cd ..

npm install
npx cap sync

# Open Android App
npx cap run android
# Open in Android Studio
# npx cap open android