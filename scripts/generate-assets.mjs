// Generate Android (and later iOS) app icons + splash screens from resources/.
//
// Sources:
//   resources/icon.png    required, square PNG (>=1024 recommended)
//   resources/splash.png  optional override; otherwise the splash is the icon
//                         centered on SPLASH_BG
//
// The adaptive-icon background is a <color> resource (values/ic_launcher_background.xml),
// not a bitmap — so we don't emit ic_launcher_background.png. Night/dark variants are
// not emitted either; Android falls back through the qualifier hierarchy to the
// default drawable when no night-mode resource is found, which is the desired
// behavior for a project with only one icon source.
//
// Run: npm run generate-assets

import sharp from 'sharp';
import { mkdir, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const RES_SRC = join(ROOT, 'resources');
const ANDROID_RES = join(ROOT, 'android/app/src/main/res');

const SPLASH_BG = '#ffffff';

// Android density buckets. Multipliers are relative to mdpi (1dp = 1px).
const DENSITIES = [
    { name: 'ldpi', mul: 0.75 },
    { name: 'mdpi', mul: 1 },
    { name: 'hdpi', mul: 1.5 },
    { name: 'xhdpi', mul: 2 },
    { name: 'xxhdpi', mul: 3 },
    { name: 'xxxhdpi', mul: 4 },
];

// Base sizes in dp. ic_launcher = 48dp (legacy launcher icon size).
// ic_launcher_{foreground,background} = 108dp (adaptive-icon canvas — the
// XML in mipmap-anydpi-v26/ insets it by 16.7% to expose the 72dp safe zone).
const ICON_BASE_DP = 48;
const ADAPTIVE_BASE_DP = 108;

// Splash baseline in dp (mdpi portrait). Landscape is the swap. These match
// the sizes @capacitor/assets produced for this project.
const SPLASH_PORT_DP = { w: 320, h: 480 };
const SPLASH_LAND_DP = { w: 480, h: 320 };

async function ensureDir(p) {
    await mkdir(p, { recursive: true });
}

async function write(dir, name, buf) {
    await ensureDir(dir);
    await writeFile(join(dir, name), buf);
}

function dp(base, mul) {
    return Math.round(base * mul);
}

async function generateLauncherIcons(iconBuf) {
    for (const { name, mul } of DENSITIES) {
        const dir = join(ANDROID_RES, `mipmap-${name}`);
        const size = dp(ICON_BASE_DP, mul);
        const png = await sharp(iconBuf).resize(size, size, { fit: 'cover' }).png().toBuffer();
        await write(dir, 'ic_launcher.png', png);
        // round: same bitmap; the XML in mipmap-anydpi-v26 references the
        // adaptive icon on Android O+, so this PNG is only used on pre-O.
        await write(dir, 'ic_launcher_round.png', png);
    }
}

async function generateAdaptiveForegrounds(iconBuf) {
    for (const { name, mul } of DENSITIES) {
        const dir = join(ANDROID_RES, `mipmap-${name}`);
        const size = dp(ADAPTIVE_BASE_DP, mul);
        const fg = await sharp(iconBuf).resize(size, size, { fit: 'cover' }).png().toBuffer();
        await write(dir, 'ic_launcher_foreground.png', fg);
    }
}

async function compositeSplash(iconBuf, w, h) {
    // Icon occupies ~1/3 of the shorter side, centered on a solid background.
    const iconSize = Math.round(Math.min(w, h) / 3);
    const fg = await sharp(iconBuf).resize(iconSize, iconSize, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } }).png().toBuffer();
    return sharp({
        create: { width: w, height: h, channels: 4, background: SPLASH_BG },
    })
        .composite([{ input: fg, gravity: 'center' }])
        .png()
        .toBuffer();
}

async function splashFor(splashOverride, iconBuf, w, h) {
    return splashOverride
        ? sharp(splashOverride).resize(w, h, { fit: 'cover' }).png().toBuffer()
        : compositeSplash(iconBuf, w, h);
}

async function generateSplashes(iconBuf, splashOverride) {
    for (const { name, mul } of DENSITIES) {
        const portW = dp(SPLASH_PORT_DP.w, mul);
        const portH = dp(SPLASH_PORT_DP.h, mul);
        const landW = dp(SPLASH_LAND_DP.w, mul);
        const landH = dp(SPLASH_LAND_DP.h, mul);
        await write(join(ANDROID_RES, `drawable-port-${name}`), 'splash.png', await splashFor(splashOverride, iconBuf, portW, portH));
        await write(join(ANDROID_RES, `drawable-land-${name}`), 'splash.png', await splashFor(splashOverride, iconBuf, landW, landH));
    }
    // Density-less fallback used when no density bucket matches.
    await write(join(ANDROID_RES, 'drawable'), 'splash.png', await splashFor(splashOverride, iconBuf, SPLASH_PORT_DP.w, SPLASH_PORT_DP.h));
}

async function readOptional(path) {
    return existsSync(path) ? path : null;
}

async function main() {
    const iconPath = join(RES_SRC, 'icon.png');
    if (!existsSync(iconPath)) {
        throw new Error(`Missing required source ${iconPath}`);
    }
    const splashPath = await readOptional(join(RES_SRC, 'splash.png'));

    console.log('Generating Android icons from', iconPath);
    await generateLauncherIcons(iconPath);
    await generateAdaptiveForegrounds(iconPath);

    console.log('Generating Android splash screens');
    await generateSplashes(iconPath, splashPath);

    console.log('Done.');
}

main().catch(err => {
    console.error(err);
    process.exit(1);
});
