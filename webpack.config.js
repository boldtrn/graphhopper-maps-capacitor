const HTMLWebpackPlugin = require('html-webpack-plugin')
const CopyPlugin = require('copy-webpack-plugin')
const path = require("path");

module.exports = {
    entry: path.resolve(__dirname, 'src', 'app'),
    output: {
        path: path.resolve(__dirname, 'dist'),
        filename: 'bundle.js',
    },
    plugins: [
        new HTMLWebpackPlugin({ template: path.resolve(__dirname, 'graphhopper-maps/src/index.html') }),
        // Files the graphhopper-maps bundle loads at runtime next to itself: the maplibre-gl 6 worker
        // (see setWorkerUrl in MapLibreLayer.ts, without it vector tiles stay blank) and images like ferry.png
        new CopyPlugin({
            patterns: [
                { from: 'maplibre-gl-*.mjs', context: path.resolve(__dirname, 'graphhopper-maps/dist'), info: { minimized: true } },
                { from: '*.png', context: path.resolve(__dirname, 'graphhopper-maps/dist') },
            ],
        }),
    ],
    mode: 'production',
}
