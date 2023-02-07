#!/bin/bash

cp config.js graphhopper-maps/

# Build GraphHopper Maps
cd graphhopper-maps
npm install
rm dist/bundle*js
npm run build
# we have a unique ID attached to the bundle file due to caching; remove this here
mv dist/bundle.*.js dist/bundle.js
cd ..

npm install

# Create dist folder
npm run build
# config.js is no longer in bundle.js
cp config.js dist/

# Create launch and splash icons
npx capacitor-assets generate --android --iconBackgroundColor '#eeeeee' --iconBackgroundColorDark '#222222' --splashBackgroundColor '#ffffff' --splashBackgroundColorDark '#ffffff'

npx cap sync

# Build Android
cd android
./gradlew assembleDebug
cd ..

# Open Android App
# npx cap run android

# Open in Android Studio
# npx cap open android