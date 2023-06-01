// This is not required for deployment. Execute only to update streamtide contract with a new version

const {Status} = require ("./utils.js");
const {env, smart_contracts_path} = require ('../truffle.js');
const {readSmartContractsFile, getSmartContractAddress, setSmartContractAddress, writeSmartContracts} = require("./utils");

const StreamtideForwarder = artifacts.require("MutableForwarder");
const Streamtide = artifacts.require("MVPCLR");

const smartContracts = readSmartContractsFile(smart_contracts_path);
const streamtideFwdAddr = getSmartContractAddress(smartContracts, ":streamtide-fwd");


module.exports = async(deployer, network, accounts) => {
    const address = accounts [0];
    const gas = 4e6;
    const opts = {gas: gas, from: address};

    await deployer;

    let status = new Status("99");

    const sk = {
        streamtideAddr: "streamtideAddr"
    }

    await status.step(async () => {
        const streamtide = await deployer.deploy(Streamtide, Object.assign(opts, {gas: 2000000}));
        return {[sk.streamtideAddr]: streamtide.address};
    });

    await status.step(async () => {
        const streamtideAddr = status.getValue(sk.streamtideAddr);

        const streamtideForwarder = await StreamtideForwarder.at(streamtideFwdAddr);

        await streamtideForwarder.setTarget(streamtideAddr);
    });

    setSmartContractAddress(smartContracts, ":streamtide", status.getValue(sk.streamtideAddr));

    writeSmartContracts(smart_contracts_path, smartContracts, env);

    status.clean();
    console.log ("Done");

}
