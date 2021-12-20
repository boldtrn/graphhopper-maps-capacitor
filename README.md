# Graphhopper Maps Capacitor
[Capacitor](https://capacitorjs.com/) wrapper for the `navi` branch of [GraphHopper Maps](https://github.com/graphhopper/graphhopper-maps). 

There are many known limitations for turn-by-turn navigation in the browser and if it is not possible to fix them in the browser we can fix them here.

## Install

We provide automatically generated build artifacts for every commit. You can find them in the [actions tab](https://github.com/boldtrn/graphhopper-maps-capacitor/actions).

## Build

Make sure you have all the [required dependencies](https://capacitorjs.com/docs/v2/getting-started/dependencies)

Then run: `./build.sh`

Note: you can either run the app straight away or open it in Android Studio. You can generate an APK in Android Studio,
or debug the app etc.

**Open Android App**

`npx cap run android`

**Open in Android Studio**

`npx cap open android`

## Release Update Version

1. Make sure to pull the latest version of the submodule "graphhopper-maps". (Automated build tools should do this anyway)
2. Open `android/app/build.grade` and increment the `versionCode` and set the `versionName`. 
Important for F-Droid: in the [metadata/com.graphhopper.maps.yml](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/com.graphhopper.maps.yml) AutoUpdateMode: Version v%v, so the tag name must be `v<versionName>`
3. Write changelog in `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
4. Commit
5. Tag commit
   1. Create a tag using `git tag -a v1.0.1`
   2. Push tags `git push origin --tags`
