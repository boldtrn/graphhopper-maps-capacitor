# Create launch and splash icons
npm run generate-assets

./build.sh

# cd android &&./gradlew assembleDebug && cd ..

npx cap run android

# Open in Android Studio
# npx cap open android