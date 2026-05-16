#!/bin/bash

set -e

cp config.js graphhopper-maps/

echo "Building capacitor-maplibre-navigation plugin..."
cd capacitor-maplibre-navigation
npm ci --ignore-scripts 
npm run build
cd ..


echo "Building graphhopper-maps..."
cd graphhopper-maps
npm ci --ignore-scripts
rm -f dist/bundle*js
npm run fdroid
# we have a unique ID attached to the bundle file due to caching; remove this here
mv dist/bundle.*.js dist/bundle.js
cd ..


# for now avoid --ignore-scripts as the sharp module needs this and this does not work
# npm install --ignore-scripts sharp @img/sharp-linux-x64

echo "Building wrapper app..."
npm ci

# Create dist folder
npm run build
# config.js is no longer in bundle.js
cp config.js dist/

# Create launch and splash icons
npm run generate-assets

# Bundle web translations into the navigation plugin so native UI can reuse them
mkdir -p capacitor-maplibre-navigation/android/src/main/assets
cp graphhopper-maps/src/translation/tr.json capacitor-maplibre-navigation/android/src/main/assets/tr.json

npx cap sync

# Build Android
cd android
./gradlew assembleDebug
cd ..

# Open Android App
# npx cap run android

# Open in Android Studio
# npx cap open android