// SPDX-License-Identifier: MIT

pragma solidity ^0.8.0;

import "@openzeppelin/contracts-upgradeable/access/OwnableUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/Initializable.sol";
import "@openzeppelin/contracts/utils/Context.sol";



contract MVPCLR is OwnableUpgradeable {
  
    event AdminAdded(address _admin);
    event AdminRemoved(address _admin);
    event BlacklistedAdded(address _blacklisted);
    event BlacklistedRemoved(address _blacklisted);
    event MatchingPoolFilled(uint256 amount);

    event PatronAdded(address addr);

    event RoundStarted(uint256 roundStart, uint256 roundId, uint256 roundDuration);
    event RoundClosed(uint256 roundId); // Added event
    event MatchingPoolDonation(address sender, uint256 value, uint256 roundId);
    event Distribute(address to, uint256 amount, uint256 roundId);
    event DistributeRound(uint256 roundId, uint256 amount);
    

    event Donate(
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
    uint256 public matchingPool;
    uint256 roundId;
    uint256 public lastActiveRoundId;


    mapping(address => bool) public isAdmin;
    mapping(address => bool) public isPatron;
    mapping(address => bool) public isBlacklisted;
    
    address public multisigAddress;

    function construct(address _multisigAddress) external initializer {
    __Ownable_init(); // Add this line to initialize the OwnableUpgradeable contract
    multisigAddress = _multisigAddress;
    roundId = 0;
    lastActiveRoundId = 0;
}

    function setMultisigAddress(address _multisigAddress) external onlyMultisig {
    multisigAddress = _multisigAddress;
}

    function fillUpMatchingPool() public payable onlyAdmin {
    require(msg.value > 0, "MVPCLR:fillUpMatchingPool - No value provided");
    emit MatchingPoolDonation(msg.sender, msg.value, roundId);
}


    function closeRound() public onlyAdmin {
        roundDuration = 0;
        roundId = 0;
        emit RoundClosed(lastActiveRoundId); // Added event emission
}

    function roundIsClosed() public view returns (bool) {
        return roundDuration == 0 || roundStart + roundDuration <= getBlockTimestamp();
}

    function startRound(uint256 _roundDuration) public payable onlyAdmin {
        require(roundIsClosed(), "MVPCLR: startRound - Previous round not yet closed");
        lastActiveRoundId += 1;
        roundId = lastActiveRoundId;
        require(_roundDuration < 31536000, "MVPCLR: round duration too long");
        roundDuration = _roundDuration;
        roundStart = getBlockTimestamp();
        emit RoundStarted(roundStart, roundId, roundDuration);
        emit MatchingPoolDonation(msg.sender, msg.value, roundId); // Emit event for the added funds

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

    function donate(address[] memory patronAddresses, uint256[] memory amounts) public payable {
    require(patronAddresses.length == amounts.length, "CLR:donate - Mismatch between number of patrons and amounts");
    uint256 totalAmount = 0;
    uint256 donationRoundId = roundIsClosed() ? 0 : roundId;
    for (uint256 i = 0; i < patronAddresses.length; i++) {
        address patronAddress = patronAddresses[i];
        uint256 amount = amounts[i];
        totalAmount += amount;
        require(!isBlacklisted[_msgSender()], "Sender address is blacklisted");
        require(isPatron[patronAddress], "CLR:donate - Not a valid recipient");
        emit Donate(_msgSender(), amount, patronAddress, donationRoundId);
        bool success = payable(patronAddress).send(amount);
        require(success, "CLR:donate - Failed to send funds to recipient");
    }

    require(totalAmount <= msg.value, "CLR:donate - Total amount donated is greater than the value sent");
    // transfer the donated funds to the contract
    // payable(address(this)).transfer(msg.value);
}


    function distribute(address payable[] memory patrons, uint[] memory amounts) public onlyAdmin {
    require(patrons.length == amounts.length, "Length of patrons and amounts must be the same");
    uint256 totalAmount = 0; // Store total amount to be distributed

    // Loop through the list of patrons and distribute the funds to each address
    for (uint i = 0; i < patrons.length; i++) {
        // Make sure the recipient address is a valid patron address
        require(isPatron[patrons[i]], "CLR:distribute - Not a valid recipient");
        patrons[i].transfer(amounts[i]); // Reverts transaction if transfer fails
        emit Distribute(patrons[i], amounts[i], roundId);
        totalAmount += amounts[i];  // Add the amount to totalAmount
    }
//    matchingPool -= totalAmount; // Subtract the total distributed amount from the matching pool
    emit DistributeRound(roundId, totalAmount);
}
    
    //only designated multisig address can call this function
    function withdrawFunds(uint256 amount) external onlyMultisig {
    require(address(this).balance >= amount, "Insufficient funds in contract");
    payable(multisigAddress).transfer(amount);
}



    // receive donation for the matching pool
    receive() external payable {
    require(roundStart == 0 || getBlockTimestamp() < roundStart + roundDuration,"CLR:receive closed");
    emit MatchingPoolDonation(_msgSender(), msg.value, roundId);
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
