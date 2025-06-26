const {readSmartContractsFile, getSmartContractAddress} = require ("./utils.js");
const edn = require("jsedn");
const {smart_contracts_path, parameters} = require ('../truffle.js');

const [_, multichainSmartContracts] = readSmartContractsFile(smart_contracts_path);

const MatchingPool = artifacts.require("MatchingPool");

module.exports = async(deployer, network, accounts) => {
    const address = accounts [0];
    const gas = 4e6;
    const opts = {gas: gas, from: address};

    await deployer;

    const chainSmartContracts = edn.atPath(multichainSmartContracts, ":" + deployer.network_id);
    const matchingPoolFwdAddr = getSmartContractAddress(chainSmartContracts, ":matching-pool-fwd");

    console.log(`Matching Pool Addr: ${matchingPoolFwdAddr}. Chain: ${deployer.network_id}`);

    const matchingPoolFwd = await MatchingPool.at(matchingPoolFwdAddr);

    for (const admin of parameters.admins) {
        console.log ("Adding admin: " + admin);
        await matchingPoolFwd.addAdmin(admin, Object.assign(opts, {gas: 5000000}));
    }
}
