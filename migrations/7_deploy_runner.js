const {Status} = require ("./utils.js");
const {env, smart_contracts_path} = require ('../truffle.js');
const {readSmartContractsFile, getSmartContractAddress, writeSmartContracts} = require("./utils");
const edn = require("jsedn");

const [smartContracts, multichainSmartContracts] = readSmartContractsFile(smart_contracts_path);

const streamtideFwdAddr = getSmartContractAddress(smartContracts, ":streamtide-fwd");
const Streamtide = artifacts.require("MVPCLR");
const DonationRunner = artifacts.require("DonationRunner");


module.exports = async(deployer, network, accounts) => {
    const address = accounts [0];
    const gas = 4e6;
    const opts = {gas: gas, from: address};

    await deployer;

    let status = new Status("7");

    const sk = {
        runnerAddr: "runnerAddr"
    }

    await status.step(async () => {
        const runner = await deployer.deploy(DonationRunner, streamtideFwdAddr, Object.assign(opts, {gas: 3000000}));
        return {[sk.runnerAddr]: runner.address};
    });

    await status.step(async () => {
        const runnerAddr = status.getValue(sk.runnerAddr);

        const streamtide = await Streamtide.at(streamtideFwdAddr);

        await streamtide.setRunner(runnerAddr);
    });

    smartContracts.set(edn.kw(":donation-runner"),
        new edn.Map([edn.kw(":name"), "DonationRunner",
            edn.kw(":address"), status.getValue(sk.runnerAddr)]));

    writeSmartContracts(smart_contracts_path, smartContracts, multichainSmartContracts, env);

    status.clean();
    console.log ("Done");
}
