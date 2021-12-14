#!/bin/bash

cp config.js graphhopper-maps/

# Build GraphHopper Maps
# TODO: sync submodule?
cd graphhopper-maps
npm install
npm run build
cd ..

npm install

# Create Dist folder
npm run build

# Create launch and splash icons
cordova-res android --skip-config --copy

npx cap sync

# Build Android
cd android
./gradlew assembleDebug
cd ..

# Open Android App
# npx cap run android

# Open in Android Studio
# npx cap open android