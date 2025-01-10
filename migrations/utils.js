const fs = require('fs');
const path = require('path');
const edn = require("jsedn");

function smartContractsTemplate (map, multichainmap, env) {
  return `(ns streamtide.shared.smart-contracts-${env})
  (def smart-contracts
    ${map})
  (def multichain-smart-contracts
    ${multichainmap})
`;
}

function encodeSmartContracts (smartContracts) {
  if (Array.isArray(smartContracts)) {
    smartContracts = new edn.Map(smartContracts);
  }
  var contracts = edn.encode(smartContracts);
  console.log(contracts);
  return contracts;
}

function extractEdnDefs(ednString) {
  const ednObjects = {};
  const defPattern = /\(def\s+([\w\-]+)\s+([\s\S]*?)\)/g;
  let match;

  while ((match = defPattern.exec(ednString)) !== null) {
    const key = match[1];
    const value = match[2];
    ednObjects[key] = edn.parse(value);
  }

  return ednObjects;
}

const utils = {

  last: (array) => {
    return array[array.length - 1];
  },

  copy: (srcName, dstName, contracts_build_directory, network, address) => {

    let buildPath = contracts_build_directory;

    const srcPath = buildPath + srcName + '.json';
    const dstPath = buildPath + dstName + '.json';

    const data = require(srcPath);
    data.contractName = dstName;

    delete Object.assign(data.ast.exportedSymbols, {[dstName]: data.ast.exportedSymbols[srcName] })[srcName];

    const o = data.ast.nodes.find(node => node.nodeType === 'ContractDefinition'
        && (node.name === srcName || node.name === srcName))

    if (o){
      o.name = dstName;
      o.canonicalName = dstName;
    }

    // Save address when given
    if (network && address) {
      data.networks = {};

      // Copy existing networks
      if (fs.existsSync(dstPath)) {
        const existing = require(dstPath);
        data.networks = existing.networks;
      }

      data.networks[network.toString()] = {
        address: address
      };
    }
    fs.writeFileSync(dstPath, JSON.stringify(data, null, 2), { flag: 'w' });
  },

  linkBytecode: (contract, placeholder, replacement) => {
    var placeholder = placeholder.replace('0x', '');
    var replacement = replacement.replace('0x', '');
    var bytecode = contract.bytecode.split(placeholder).join(replacement);
    contract.bytecode = bytecode;
  },

  readSmartContractsFile: (smartContractsPath) => {
    var content = fs.readFileSync(smartContractsPath, "utf8");

    let defs = extractEdnDefs(content);
    return [defs["smart-contracts"], defs["multichain-smart-contracts"]];
  },

  setSmartContractAddress: (smartContracts, contractKey, newAddress) => {
  var contract = edn.atPath(smartContracts, contractKey);
  contract = contract.set(edn.kw(":address"), newAddress);
  return smartContracts.set(edn.kw(contractKey), contract);
  },

  getSmartContractAddress: (smartContracts, contractKey) => {
    try {
      return edn.atPath(smartContracts, contractKey + " :address");
    } catch (e) {
      return null;
    }
  },

  writeSmartContracts: (smartContractsPath, smartContracts, multichainSmartContracts, env) => {
    console.log("Writing to smart contract file: " + smartContractsPath);
    fs.writeFileSync(smartContractsPath, smartContractsTemplate(encodeSmartContracts(smartContracts), encodeSmartContracts(multichainSmartContracts), env));
  },

  Status: class {
    constructor(id) {
      this.id = id;
      this.currentStep = 0;
      this.lastStep = -1;
      this.values = {};
      this._loadStatus();
    }

    async step(fn) {
      if (this.lastStep < this.currentStep) {
        console.log("Executing step: " + this.currentStep);
        let values = await fn(this);
        Object.assign(this.values, values);
        this.lastStep++;
        this._saveStatus();
      } else {
        console.log("Skipping previously executed step: " + this.currentStep);
      }
      this.currentStep++;
    }

    getValue(key) {
      return this.values[key];
    }

    _filename() {
      return path.resolve(__dirname, this.id + '_status.json');
    }

    _loadStatus() {
      if (fs.existsSync(this._filename())) {
        console.log("Previous execution detected. Loading status to resume")
        try {
          let data = fs.readFileSync(this._filename());
          let st = JSON.parse(data.toString());
          this.lastStep = st['lastStep'];
          this.values = st['values'];
        } catch (err) {
          console.warn("Failed to load status");
        }
      }
    }

    _saveStatus() {
      let data = JSON.stringify({'lastStep': this.lastStep, 'values': this.values});
      try {
        fs.writeFileSync(this._filename(), data)
      } catch (err) {
        console.warn("Cannot save state", err);
      }
    }

    clean() {
      try {
        fs.unlinkSync(this._filename());
      } catch (err) {
        console.warn("Failed to clean status", err);
      }
    }
  }

};

module.exports = utils;
