# Create launch and splash icons
npm run generate-assets

./build.sh

# Build Android
cd android
./gradlew assembleDebug
cd ..

# Open Android App
# npx cap run android

# Open in Android Studio
# npx cap open android