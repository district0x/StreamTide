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
        admins: ["0xaFcF1a2bc71AcF041c93012A2E552e31026dFEAB"]
    },
    "qa" : {
        multiSig: "0x11b23AE13EBACc03Fa0af256fdED729439A45ab5",
        admins: [
            "0x11b23AE13EBACc03Fa0af256fdED729439A45ab5",
            "0xb078844477A5420cB627C1961B30ED33E0126973",
            "0x0A0A8610F57fE41EC26D5163d1Eb986cE598dc5F"]
    },
    "prod" : {
        multiSig: "0xf7190fa8c89F7c57ff77b8Bc0Da85e9a2daF70Ad",
        admins: [
            "0xf7190fa8c89F7c57ff77b8Bc0Da85e9a2daF70Ad",
            "0xb078844477A5420cB627C1961B30ED33E0126973",
            "0x0A0A8610F57fE41EC26D5163d1Eb986cE598dc5F"]
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
        "alchemy-arbitrum-goerli": {
            provider: () => new HDWalletProvider(process.env.ARBITRUM_GOERLI_PRIV_KEY, "https://arb-goerli.g.alchemy.com/v2/" + process.env.ALCHEMY_API_KEY),
            network_id: 421613,
            gas: 6e6,
            gasPrice: 1e9,
            skipDryRun: true
        },
        "infura-mainnet": {
            provider: () => new HDWalletProvider(process.env.MAINNET_PRIV_KEY, "https://mainnet.infura.io/v3/" + process.env.INFURA_API_KEY),
            network_id: 1,
            gas: 6e6,
            gasPrice: 9e9,
            skipDryRun: true
        },
        "alchemy-arbitrum-mainnet": {
            provider: () => new HDWalletProvider(process.env.ARBITRUM_PRIV_KEY, "https://arb-mainnet.g.alchemy.com/v2/" + process.env.ALCHEMY_API_KEY),
            network_id: 42161,
            gas: 6e7,
            gasPrice: 3e8,
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
