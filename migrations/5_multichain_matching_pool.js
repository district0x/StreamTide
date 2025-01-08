const {Status, copy, linkBytecode, readSmartContractsFile, writeSmartContracts} = require ("./utils.js");
const edn = require("jsedn");
const {env, contracts_build_directory, smart_contracts_path, parameters} = require ('../truffle.js');

copy ("MutableForwarder", "MatchingPoolForwarder", contracts_build_directory);
const MatchingPoolForwarder = artifacts.require("MatchingPoolForwarder");
const MatchingPool = artifacts.require("MatchingPool");

let [smartContracts, multichainSmartContracts] = readSmartContractsFile(smart_contracts_path);

const forwarderTargetPlaceholder = "beefbeefbeefbeefbeefbeefbeefbeefbeefbeef";

module.exports = async(deployer, network, accounts) => {
    const address = accounts [0];
    const gas = 4e6;
    const opts = {gas: gas, from: address};

    await deployer;

    let status = new Status("5");

    const sk = {
        matchingPoolAddr: "matchingPoolAddr",
        matchingPoolForwarderAddr: "matchingPoolForwarderAddr"
    }

    await status.step(async () => {
        const matchingPool = await deployer.deploy(MatchingPool, Object.assign(opts, {gas: 20000000}));
        return {[sk.matchingPoolAddr]: matchingPool.address};
    });

    await status.step(async () => {
        const matchingPoolAddr = status.getValue(sk.matchingPoolAddr);

        linkBytecode(MatchingPoolForwarder, forwarderTargetPlaceholder, matchingPoolAddr);
        const matchingPoolForwarder = await deployer.deploy(MatchingPoolForwarder, Object.assign(opts, {gas: 5000000}));
        return {[sk.matchingPoolForwarderAddr]: matchingPoolForwarder.address};
    });

    await status.step(async () => {
        const matchingPoolForwarderAddr = status.getValue(sk.matchingPoolForwarderAddr);

        const matchingPoolForwarder = await MatchingPool.at(matchingPoolForwarderAddr);
        await matchingPoolForwarder.construct(parameters.multiSig);
    });

    if (!multichainSmartContracts) {
        multichainSmartContracts = new edn.Map();
    }

    multichainSmartContracts.set(edn.kw(":" + deployer.network_id),
        new edn.Map([
            edn.kw(":matching-pool"), new edn.Map([edn.kw(":name"), "MatchingPool",
                edn.kw(":address"), status.getValue(sk.matchingPoolAddr)]),

            edn.kw(":matching-pool-fwd"), new edn.Map([edn.kw(":name"), "MutableForwarder",
                edn.kw(":address"), status.getValue(sk.matchingPoolForwarderAddr),
                edn.kw(":forwards-to"), edn.kw(":matching-pool")])
        ]));

    writeSmartContracts(smart_contracts_path, smartContracts, multichainSmartContracts, env);

    status.clean();
    console.log ("Done");

}
