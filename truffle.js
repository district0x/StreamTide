'use strict';

const STREAMTIDE_ENV = process.env.STREAMTIDE_ENV || "dev";
const HDWalletProvider = require("@truffle/hdwallet-provider");
require('dotenv').config()  // Store environment-specific variable from '.env' to process.env

const smartContractsPaths = {
    "dev" : '/src/streamtide/shared/smart_contracts_dev.cljs',
    "qa" : '/src/streamtide/shared/smart_contracts_qa.cljs',
    "prod" :'/src/streamtide/shared/smart_contracts_prod.cljs'
};

let parameters = {
    "dev" : {
        multiSig: "0x4c3F13898913F15F12F902d6480178484063A6Fb",
        admins: ["0x11b23AE13EBACc03Fa0af256fdED729439A45ab5"]
    },
    "qa" : {
        multiSig: "TBD",
        admins: []
    },
    "prod" : {
        multiSig: "TBD",
        admins: []
    }
};

module.exports = {
    env: STREAMTIDE_ENV,
    smart_contracts_path: __dirname + smartContractsPaths [STREAMTIDE_ENV],
    contracts_build_directory: __dirname + '/resources/public/contracts/build/',
    parameters : parameters [STREAMTIDE_ENV],
    networks: {
        "ganache": {
            host: 'localhost',
            port: 8545,
            gas: 6e6, // gas limit
            gasPrice: 20e9, // 20 gwei, default for ganache
            network_id: '*'
        },
        "infura-goerli": {
            provider: () => new HDWalletProvider(process.env.GOERLI_PRIV_KEY, "https://goerli.infura.io/v3/" + process.env.INFURA_API_KEY),
            network_id: 5,
            gas: 6e6,
            gasPrice: 6e9,
            skipDryRun: true
        },
        "infura-mainnet": {
            provider: () => new HDWalletProvider(process.env.MAINNET_PRIV_KEY, "https://mainnet.infura.io/v3/" + process.env.INFURA_API_KEY),
            network_id: 1,
            gas: 6e6,
            gasPrice: 9e9,
            skipDryRun: true
        }
    },
    compilers: {
        solc: {
            version: "0.8.19",
            settings: {
                optimizer: {
                    enabled: true
                }
            }
        }
    }
};
