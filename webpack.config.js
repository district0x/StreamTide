const path = require('path');
const webpack = require("webpack");
const current_mode = process.env.STREAMTIDE_ENV in ['prod', 'qa'] ? 'production' : 'development';

module.exports = {
    entry: './target/index.js',
    output: {
        filename: 'libs.js',
        path: path.resolve(__dirname, 'resources', 'public', 'js'),
    },
    resolve: {
        fallback: {
            "fs": false,
            "tls": false,
            "net": false,
            "path": false,
            "zlib": false,
            "http": false,
            "https": false,
            "stream": require.resolve("stream-browserify"),
            "os": false,
            "crypto": false,
            "buffer": require.resolve("buffer"),
            "process/browser": require.resolve('process/browser'),
        }
    },
    plugins: [
        new webpack.ProvidePlugin({
            process: 'process/browser'
        }),
        new webpack.ProvidePlugin({
            Buffer: ['buffer', 'Buffer'],
        }),
    ],
    mode: current_mode
};
