// SPDX-License-Identifier: MIT

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";

contract DonationRunner {
    address public immutable parent;

    constructor(address _parent) {
        parent = _parent;
    }

    modifier onlyParent() {
        require(msg.sender == parent, "Runner: Only parent");
        _;
    }

    /**
     * @dev Receives ETH, executes the call, and sweeps tokens to the beneficiary.
     * @param target The external contract to call.
     * @param callData The payload for the call.
     * @param token The token address to check for gains (optional).
     * @param beneficiary The address (user) who receives the gained tokens.
     */
    function execute(
        address target,
        bytes calldata callData,
        address token,
        address beneficiary
    ) external payable onlyParent returns (uint256) {
        // 1. Execute the arbitrary call
        (bool success, bytes memory returndata) = payable(target).call{value: msg.value}(callData);

        // Propagate revert message if failed
        if (!success) {
            if (returndata.length < 68) revert("Runner: call failed");
            assembly {
                returndata := add(returndata, 0x04)
            }
            revert(abi.decode(returndata, (string)));
        }

        // 2. Sweep Tokens (If the external call resulted in a swap/mint)
        if (token != address(0)) {
            uint256 balance = IERC20(token).balanceOf(address(this));
            if (balance > 0) {
                IERC20(token).transfer(beneficiary, balance);
                return balance;
            }
        }
        return 0;
    }
}