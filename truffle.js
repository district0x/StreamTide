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
        multiSig: "0x694D22629793B210e0C90324b4F9e571A25BD633",
        admins: ["0x694D22629793B210e0C90324b4F9e571A25BD633"],
        lastRound: 15,
        patrons: [
            "0xFB55734bACBca7d943994cA51c8049e0577A5c7f",
            "0x694D22629793B210e0C90324b4F9e571A25BD633",
            "0x11b23AE13EBACc03Fa0af256fdED729439A45ab5"]
    },
    "qa" : {
        multiSig: "0x11b23AE13EBACc03Fa0af256fdED729439A45ab5",
        admins: [
            "0x11b23AE13EBACc03Fa0af256fdED729439A45ab5",
            "0xb078844477A5420cB627C1961B30ED33E0126973",
            "0x0A0A8610F57fE41EC26D5163d1Eb986cE598dc5F",
            "0x0940f7D6E7ad832e0085533DD2a114b424d5E83A",
            "0xF256222EB43fdB2CFAD1f8Be72575F6b01Dae295"
        ],
        lastRound: 6
    },
    "prod" : {
        multiSig: "0xf7190fa8c89F7c57ff77b8Bc0Da85e9a2daF70Ad",
        admins: [
            "0xf7190fa8c89F7c57ff77b8Bc0Da85e9a2daF70Ad",
            "0xb078844477A5420cB627C1961B30ED33E0126973",
            "0x0A0A8610F57fE41EC26D5163d1Eb986cE598dc5F",
            "0x0940f7D6E7ad832e0085533DD2a114b424d5E83A"],
        lastRound: 0
    }
};

module.exports = {
    env: STREAMTIDE_ENV,
    smart_contracts_path: __dirname + smartContractsPaths [STREAMTIDE_ENV],
    contracts_build_directory: __dirname + '/resources/public/contracts/build/',
    parameters : parameters [STREAMTIDE_ENV],
    networks: {
        "ganache": {
            host: '127.0.0.1',
            port: 8545,
            gas: 6e6, // gas limit
            gasPrice: 20e9, // 20 gwei, default for ganache
            network_id: '*'
        },
        "alchemy-base-sepolia": {
            provider: () => new HDWalletProvider(process.env.BASE_SEPOLIA_PRIV_KEY, "https://base-sepolia.g.alchemy.com/v2/" + process.env.ALCHEMY_API_KEY),
            network_id: 84532,
            gas: 6e6,
            gasPrice: 1e9,
            skipDryRun: true
        },
        "alchemy-base-mainnet": {
            provider: () => new HDWalletProvider(process.env.BASE_PRIV_KEY, "https://base-mainnet.g.alchemy.com/v2/" + process.env.ALCHEMY_API_KEY),
            network_id: 8453,
            gas: 6e6,
            gasPrice: 4e6,
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
