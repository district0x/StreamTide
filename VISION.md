# StreamTide Protocol Vision

> **Note:** This document outlines a potential future evolution of StreamTide. Current implementation is a centralized grants platform (see [README.md](./README.md)). The ideas here are in active research and not yet committed to the roadmap.

## The Problem

Creator tokens follow a predictable pattern:
1. Launch with initial hype
2. Early buyers create volume
3. Buzz fades, no new demand
4. Early holders want to exit but lack liquidity
5. Token slowly bleeds or cliff dumps
6. Creator abandons, token flatlines

**Core issue:** No structural reason for sustained demand. Pure speculation with no yield, utility, or institutional buying.

## The Insight

What if grant matching funds didn't just give creators cash, but instead:
- Bought their tokens on bonding curves (creating demand)
- Provided liquidity and **burned the LP tokens** (permanent floor)
- Did this systematically across multiple rounds (sustained support)

This creates what creator tokens are missing: **predictable, structural buying + permanent liquidity**.

## The Vision: Permissionless Curator Pools

### Architecture

```
StreamTide Protocol (Umbrella)
├─ Open source smart contracts
├─ Permissionless deployment
└─ Individual curator pools

    Curator Pool (Anyone can create)
    ├─ Curator deposits ETH (becomes funder)
    ├─ Curates list of quality creators
    ├─ Community donates to signal support
    ├─ Quadratic funding calculates matching
    ├─ Matching buys tokens + burns LP
    └─ Graduation bonuses for success
```

### How It Works

**1. Pool Creation**
- Anyone deposits ETH to create a curator pool
- Curator = funder (perfect alignment)
- Can accept external funding (optional)

**2. Curation**
- Curator approves creators (quality filter)
- Community votes via donations (demand signal)
- Quadratic funding weighs contributions

**3. Matching Execution**
- Matching funds buy creator tokens on bonding curves
- Provides liquidity (ETH + tokens)
- **Burns LP tokens** (permanent, irrevocable)

**4. Graduation Mechanics**
- Token hits bonding curve threshold
- Massive LP injection + burn (bonus)
- Curator takes 20% profit (sustainability)
- Voters receive 10% airdrop (incentive)
- Hold 70% long-term (alignment)

**5. Cycle Repeats**
- Multiple rounds of buying
- Sustained demand over time
- Permanent liquidity accumulation

### Why This Works

**For Creators:**
- Systematic buying pressure (not just launch hype)
- Permanent liquidity (holders can exit)
- Graduation milestone is meaningful
- Sustained support, not one-time grant

**For Supporters:**
- Backing winners gets rewarded (voter airdrops)
- Tokens have permanent floor (LP burned)
- Exit liquidity exists (not trapped)
- Community aligned around success

**For Curators:**
- Earn from successful picks (20% graduation profit)
- Build portfolio of creator tokens
- Reputation for quality curation
- Sustainable business model

**For Grant Funders:**
- Can contribute to successful curator pools
- Exposure to creator economy upside
- Professional curation (don't pick themselves)
- Portfolio approach (diversification)

## Technical Components

### Smart Contracts

**CuratorPoolFactory.sol**
- Permissionless pool deployment
- Registry of all pools
- Optional protocol fee

**CuratorPool.sol**
- Curator management
- Creator approval
- Round execution
- Matching calculation
- Fund management

**TokenBuyer.sol**
- Integration with bonding curves (Vibe Market, Toshi Mart, Zora, pump.fun)
- Multi-chain support
- Slippage protection
- MEV protection (commit-reveal or Flashbots)

**LiquidityManager.sol**
- LP provision to DEXs/bonding curves
- LP token burning (permanent lock)
- Graduation bonus execution
- Profit taking for curator

**CuratorReputation.sol** (future)
- On-chain track record
- Success rate tracking
- Volume deployed
- Natural curation of curators

### Frontend/Backend

**Curator Dashboard**
- Deploy new pool
- Approve creators
- Configure rounds
- Execute matching
- View portfolio

**Pool Browser**
- Discover curator pools
- Filter by focus (music, art, gaming)
- View curator track record
- Donate to creators

**Creator Interface**
- Request inclusion in pools
- View matching received
- Track token performance

## Economic Model

### Curator Revenue
- 20% of holdings at graduation (profit taking)
- Appreciation of 70% long-term holdings
- Optional: Management fees from external funders
- Optional: Protocol fees (if treasury enabled)

### Sustainability
- Curator profits fund future rounds
- Successful pools attract external capital
- Network effects (more curators = more liquidity)
- Self-sustaining after bootstrap

### Risk Mitigation
- Curator has own capital at risk (alignment)
- Quadratic funding filters sybils
- LP burning is permanent (can't rug)
- Graduation bonuses require success (no farming)
- Community votes prevent bad picks

## Regulatory Positioning

**What StreamTide Is:**
- Open source software for liquidity provision
- Market making infrastructure
- Buying existing assets (not creating securities)
- Permissionless protocol (like Uniswap)

**What StreamTide Is NOT:**
- Investment advisor (curators make own decisions)
- Securities dealer (providing liquidity, not dealing)
- Investment fund (each pool independent)
- Centralized operator (protocol is permissionless)

**Regulatory approach:**
- Open source everything
- Permissionless deployment
- No central control (eventual DAO)
- Curators use own capital
- Clear: software, not service

## Inspiration & Precedents

**Rare Pepe Cards:**
- Curated quality (card scientists)
- Burns create scarcity
- Community holds (shared success)
- Successful cards worth thousands

**Gitcoin Grants:**
- Quadratic funding for quality signaling
- Multiple rounds of support
- Community-driven allocation

**Uniswap:**
- Permissionless liquidity provision
- Anyone can create pools
- Decentralized protocol

**district0x:**
- Permissionless district deployment
- Open source infrastructure
- DAO governance
- Network effects

**StreamTide combines these models:**
- Curator quality filter (Rare Pepes)
- Quadratic community signals (Gitcoin)
- Permissionless pools (Uniswap)
- Protocol architecture (district0x)

## Phases

### Phase 1: Validation (Current)
- Manual test: Buy tokens + burn LP with existing matching
- Document results
- Prove concept works
- Get community feedback

### Phase 2: Development (3-6 months)
- Build core smart contracts
- Integrate with launchpads (Vibe, Toshi, Zora)
- Implement LP burning mechanism
- Build curator dashboard
- Test on testnet

### Phase 3: Launch (6-9 months)
- Deploy to mainnet
- Launch first curator pool (pilot)
- Onboard 2-3 additional curators
- Prove permissionless model works
- Gather metrics on token performance

### Phase 4: Scale (9-12 months)
- Open to any curator
- Launch governance token ($TIDE)
- DAO controls protocol parameters
- Multi-chain expansion (Solana, etc.)
- Full decentralization

### Phase 5: Ecosystem (12+ months)
- On-chain reputation system
- Pool coordination mechanisms
- Graduated creator index token
- Third-party integrations
- Protocol becomes infrastructure layer

## Open Questions

- Should matching be 50/50 token buys vs LP provision, or different ratio?
- How to prevent frontrunning of systematic buys? (MEV protection needed)
- Should curators be able to coordinate buys for better execution?
- What's the right graduation bonus split? (curator/voters/long-term)
- Is commit-reveal sufficient for MEV or need Flashbots?
- Should there be a protocol-level $TIDE token or just curator pools?
- How to handle cross-chain operations? (separate pools or bridge?)
- What's minimum deposit for curator pool? (anti-spam)

## Why This Could Work

**Market gap:** Creator tokens need systematic liquidity, currently don't have it.

**Proven mechanics:** Quadratic funding works (Gitcoin), burns work (Rare Pepes), permissionless pools work (Uniswap).

**Alignment:** Curators have capital at risk, profits from success, reputation matters.

**Network effects:** More curators → more liquidity → more creator success → more funders attracted.

**Moat:** First systematic liquidity protocol for creator tokens. Network effects + reputation system create defensibility.

**Precedent:** district0x successfully launched permissionless district model. Same team, same playbook.

## Risks & Mitigations

**Risk: Bad curators hurt protocol reputation**
- Mitigation: Staking + slashing, reputation system, DAO blacklist

**Risk: Frontrunning/MEV attacks drain curator capital**
- Mitigation: Commit-reveal scheme, Flashbots integration, batch auctions

**Risk: Sybil attacks on voting**
- Mitigation: Quadratic funding, social verification, curator gatekeeper

**Risk: Token dumps after graduation**
- Mitigation: Curator holds 70% long-term, voter rewards align community

**Risk: Regulatory uncertainty**
- Mitigation: Open source, permissionless, market making not investing

**Risk: Liquidity fragmentation across pools**
- Mitigation: Optional pool coordination, aggregators can build on top

## Contributing to This Vision

This is early-stage exploration. Feedback welcome:

- Is the problem real? (Do creator tokens need systematic liquidity?)
- Does the solution make sense? (Buy + burn LP vs direct grants)
- Are the incentives aligned? (Curator/voter/creator)
- What are we missing? (Attack vectors, alternatives)
- Would you use this? (As curator or creator)

Open an issue or join the discussion in Discord.

## Conclusion

StreamTide could evolve from "grants platform for creators" to "liquidity infrastructure for creator token economies." By providing systematic buying and permanent liquidity, we might solve the core problem that makes creator tokens fail.

The vision is ambitious but grounded in proven mechanisms. And unlike most DeFi experiments, this solves a real problem for real creators.

Whether this is the future of StreamTide depends on:
1. Does the manual test validate the model?
2. Can we build it securely and efficiently?
3. Will curators and creators actually use it?
4. Does it make creator tokens work as an asset class?

We'll find out.

---

**Status:** Vision document, not committed roadmap
**Last updated:** 2025-11-23
**Authors:** Brady McKenna (district0x), with contributions from community discussion
