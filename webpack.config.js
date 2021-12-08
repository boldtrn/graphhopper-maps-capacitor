const HTMLWebpackPlugin = require('html-webpack-plugin')
const path = require("path");

module.exports = {
    entry: path.resolve(__dirname, 'src', 'app'),
    output: {
        path: path.resolve(__dirname, 'dist'),
        filename: 'bundle.js',
    },
    plugins: [
        new HTMLWebpackPlugin({ template: path.resolve(__dirname, 'graphhopper-maps/src/index.html') }),
    ],
    mode: 'production',
}