const {Status, copy, linkBytecode, smartContractsTemplate} = require ("./utils.js");
const fs = require('fs');
const edn = require("jsedn");
const {env, contracts_build_directory, smart_contracts_path, parameters} = require ('../truffle.js');

const Migrations = artifacts.require("Migrations");
copy ("MutableForwarder", "StreamtideForwarder", contracts_build_directory);
const StreamtideForwarder = artifacts.require("StreamtideForwarder");
const Streamtide = artifacts.require("MVPCLR");

const forwarderTargetPlaceholder = "beefbeefbeefbeefbeefbeefbeefbeefbeefbeef";

module.exports = async(deployer, network, accounts) => {
    const address = accounts [0];
    const gas = 4e6;
    const opts = {gas: gas, from: address};

    await deployer;

    const migrations = await Migrations.deployed();

    let status = new Status("2");

    const sk = {
        streamtideAddr: "streamtideAddr",
        streamtideForwarderAddr: "streamtideForwarderAddr"
    }

    await status.step(async () => {
        const streamtide = await deployer.deploy(Streamtide, Object.assign(opts, {gas: 20000000}));
        return {[sk.streamtideAddr]: streamtide.address};
    });

    await status.step(async () => {
        const streamtideAddr = status.getValue(sk.streamtideAddr);

        linkBytecode(StreamtideForwarder, forwarderTargetPlaceholder, streamtideAddr);
        const streamtideForwarder = await deployer.deploy(StreamtideForwarder, Object.assign(opts, {gas: 5000000}));
        return {[sk.streamtideForwarderAddr]: streamtideForwarder.address};
    });

    await status.step(async () => {
        const streamtideForwarderAddr = status.getValue(sk.streamtideForwarderAddr);

        const streamtideForwarder = await Streamtide.at(streamtideForwarderAddr);
        await streamtideForwarder.construct(parameters.multiSig);
    });

    var smartContracts = edn.encode(
        new edn.Map([

            edn.kw(":migrations"), new edn.Map([edn.kw(":name"), "Migrations",
                edn.kw(":address"), migrations.address]),

            edn.kw(":streamtide"), new edn.Map([edn.kw(":name"), "MVPCLR",
                edn.kw(":address"), status.getValue(sk.streamtideAddr)]),

            edn.kw(":streamtide-fwd"), new edn.Map([edn.kw(":name"), "MutableForwarder",
                edn.kw(":address"), status.getValue(sk.streamtideForwarderAddr),
                edn.kw(":forwards-to"), edn.kw(":streamtide")])
        ]));

    console.log (smartContracts);
    fs.writeFileSync(smart_contracts_path, smartContractsTemplate (smartContracts, env));

    status.clean();
    console.log ("Done");

}
