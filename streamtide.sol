// SPDX-License-Identifier: MIT

pragma solidity ^0.8.0;

import "@openzeppelin/contracts/access/Ownable.sol";

struct Donation {
    address sender;
    uint256 amount;
}

contract MVPCLR is Ownable {
    
    event AdminAdded(address _admin);
    event AdminRemoved(address _admin);
    event BlacklistedAdded(address _blacklisted);
    event BlacklistedRemoved(address _blacklisted);

    event PatronAdded(address addr);

    event RoundStarted(uint256 roundStart, uint256 roundId, uint256 roundDuration);
    event MatchingPoolDonation(address sender, uint256 value);
    event Distribute(address to, uint256 amount);

    event Donate(
        address origin,
        address sender,
        uint256 value,
        address patronAddress,
        uint256 roundId
    );

    event FailedDistribute(
        address receiver,
        uint256 amount
    );


    uint256 public roundStart;
    uint256 public roundDuration;

    uint256 roundId;

    mapping(address => bool) public isAdmin;
    mapping(address => bool) public isPatron;
    mapping(address => bool) public isBlacklisted;

    Donation[] public donations;
    int256 index_of_last_processed_donation = -1;
    
    address public multisigAddress;

    constructor(address _multisigAddress) {
    multisigAddress = _multisigAddress;
    }

    function setMultisigAddress(address _multisigAddress) external {
    require(msg.sender == multisigAddress, "Not authorized to change multisig address");
    multisigAddress = _multisigAddress;
    }


    function closeRound() public onlyAdmin {
        roundDuration = 0;
    }

    function roundIsClosed() public view returns (bool) {
        return roundDuration != 0 && roundStart + roundDuration <= getBlockTimestamp();
    }


    function startRound(uint256 _roundDuration) public onlyAdmin {
        roundId = roundId +1;
        require(_roundDuration < 31536000, "MVPCLR: round duration too long");
        roundDuration = _roundDuration;
        roundStart = getBlockTimestamp();
        emit RoundStarted(roundStart, roundId, roundDuration);
    }

    function addAdmin(address _admin) public onlyOwner {
        isAdmin[_admin] = true;
        emit AdminAdded(_admin);
    }

    function removeAdmin(address _admin) public onlyOwner {
        require(isAdmin[_admin], "Admin not found"); // check if the address is an admin
        delete isAdmin[_admin];
        emit AdminRemoved(_admin);
    }

    function getBlockTimestamp() public view returns (uint256) {
        return block.timestamp;
    }

    function addBlacklisted(address _address) public onlyAdmin {
        isBlacklisted[_address] = true;
        emit BlacklistedAdded(_address);
    }


    function removeBlacklisted(address _address) public onlyAdmin {
        require(isBlacklisted[_address], "Address not blacklisted");
        delete isBlacklisted[_address];
        emit BlacklistedRemoved(_address);
    }

    function addPatron(address payable addr) public onlyAdmin {
        require(!isBlacklisted[addr], "Patron address is blacklisted");
        isPatron[addr] = true;
        emit PatronAdded(addr);

    }

    function donate(address payable patronAddress) public payable {
        require(!isBlacklisted[msg.sender], "Sender address is blacklisted");
        require(isPatron[patronAddress], "CLR:donate - Not a valid recipient");
        donations.push(Donation(msg.sender, msg.value));
        emit Donate(tx.origin, msg.sender, msg.value, patronAddress, roundId);
        // send the donation to the designated patron address
        if (!patronAddress.send(msg.value)) {
        emit FailedDistribute(patronAddress, msg.value);
        } else {
        emit Distribute(patronAddress, msg.value);
        }
    }

    function getDonations() public view onlyAdmin returns (Donation[] memory) {
    return donations;
    
    }
    
    
    //fail safe. Should be multisig. Bring this up on the call
    function withdrawFunds(uint256 amount) external onlyMultisig {
    require(address(this).balance >= amount, "Insufficient funds in contract");
    payable(multisigAddress).transfer(amount);
    }



    // receive donation for the matching pool
    receive() external payable {
    require(roundStart == 0 || getBlockTimestamp() < roundStart + roundDuration,"CLR:receive closed");
    emit MatchingPoolDonation(_msgSender(), msg.value);
    }

    modifier onlyAdmin() {
    require(isAdmin[msg.sender] == true, "Not an admin");
    _;
    }

    modifier onlyMultisig() {
    require(msg.sender == multisigAddress, "Not authorized");
    _;
    }

}
