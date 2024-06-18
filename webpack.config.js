const path = require('path');
const webpack = require("webpack");
const current_mode = process.env.STREAMTIDE_ENV in ['prod', 'qa'] ? 'production' : 'development';

module.exports = {
    entry: './target/index.js',
    output: {
        filename: 'libs.js',
        path: path.resolve(__dirname, 'resources', 'public', 'js'),
    },
    externals: {
        "react-native": true,
    },
    resolve: {
        alias: {
            '../react/web/ui/MediaRenderer/MediaRenderer.js' : false,
        },
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
    module: {
        rules: [
            {
                test: /NetworkSelector\.js$/,
                loader: 'string-replace-loader',
                options: {
                    search: '(const fuse_js_1 =.*)',
                    replace(match, p1) {
                        return `${p1} fuse_js_1.default = fuse_js_1;`
                    },
                    flags: 'g'
                }
            }
        ]
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
