const {readSmartContractsFile, getSmartContractAddress} = require ("./utils.js");
const {smart_contracts_path, env, parameters} = require ('../truffle.js');

const smartContracts = readSmartContractsFile(smart_contracts_path);
const streamtideFwdAddr = getSmartContractAddress(smartContracts, ":streamtide-fwd");

const Streamtide = artifacts.require("MVPCLR");

module.exports = async(deployer, network, accounts) => {
    const address = accounts [0];
    const gas = 4e6;
    const opts = {gas: gas, from: address};

    await deployer;

    const streamtideFwd = await Streamtide.at(streamtideFwdAddr);


    for (const admin of parameters.admins) {
        console.log ("Adding admin: " + admin);
        await streamtideFwd.addAdmin(admin, Object.assign(opts, {gas: 5000000}));
    }
}
