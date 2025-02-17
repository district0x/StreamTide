// SPDX-License-Identifier: MIT

pragma solidity ^0.8.0;

import "@openzeppelin/contracts-upgradeable/access/OwnableUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/Initializable.sol";
import "@openzeppelin/contracts/utils/Context.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";


contract MatchingPool is OwnableUpgradeable {

    event AdminAdded(address _admin);
    event AdminRemoved(address _admin);
    event MatchingPoolFilled(uint256 amount);

    event MatchingPoolDonation(address sender, uint256 value, uint256 roundId);
    event MatchingPoolDonationToken(address sender, uint256 value, uint256 roundId, address token);
    event Distribute(address to, uint256 amount, uint256 roundId, address token);
    event DistributeRound(uint256 roundId, uint256 amount, address token);

    mapping(address => bool) public isAdmin;

    address public multisigAddress;

    function construct(address _multisigAddress) external initializer {
        __Ownable_init(); // Add this line to initialize the OwnableUpgradeable contract
        multisigAddress = _multisigAddress;
    }

    function setMultisigAddress(address _multisigAddress) external onlyMultisig {
        multisigAddress = _multisigAddress;
    }

    function fillUpMatchingPool(uint256 roundId) public payable onlyAdmin {
        require(msg.value > 0, "MVPCLR:fillUpMatchingPool - No value provided");
        emit MatchingPoolDonation(msg.sender, msg.value, roundId);
    }

    function fillUpMatchingPoolToken(address from, address token, uint amount, uint256 roundId) public onlyAdmin {
        require(amount > 0, "MVPCLR:fillUpMatchingPoolToken - No amount provided");

        IERC20(token).transferFrom(from, address(this), amount);

        emit MatchingPoolDonationToken(from, amount, roundId, token);
    }

    function addAdmin(address _admin) public onlyOwner {
        isAdmin[_admin] = true;
        emit AdminAdded(_admin);
    }

    function removeAdmin(address _admin) public onlyOwner {
        require(isAdmin[_admin], "Admin not found");
        delete isAdmin[_admin];
        emit AdminRemoved(_admin);
    }

    function distribute(address payable[] memory patrons, uint[] memory amounts, address token, uint256 roundId) public onlyAdmin {
        require(patrons.length == amounts.length, "Length of patrons and amounts must be the same");
        uint256 totalAmount = 0; // Store total amount to be distributed

        // Loop through the list of patrons and distribute the funds to each address
        for (uint i = 0; i < patrons.length; i++) {
            if (token == address(0))
                patrons[i].transfer(amounts[i]); // Reverts transaction if transfer fails
            else
                IERC20(token).transfer(patrons[i], amounts[i]);
            emit Distribute(patrons[i], amounts[i], roundId, token);
            totalAmount += amounts[i];  // Add the amount to totalAmount
        }
        emit DistributeRound(roundId, totalAmount, token);
    }

    function withdrawFunds(uint256 amount) external onlyMultisig {
        require(address(this).balance >= amount, "Insufficient funds in contract");
        payable(multisigAddress).transfer(amount);
    }

    function withdrawERC20Funds(uint256 amount, address token) external onlyMultisig {
        IERC20(token).transfer(msg.sender, amount);
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
