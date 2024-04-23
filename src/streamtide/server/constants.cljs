(ns streamtide.server.constants)

(def web3-events
  {:streamtide/admin-added-event [:streamtide-fwd :AdminAdded]
   :streamtide/admin-removed-event [:streamtide-fwd :AdminRemoved]
   :streamtide/blacklisted-added-event [:streamtide-fwd :BlacklistedAdded]
   :streamtide/blacklisted-removed-event [:streamtide-fwd :BlacklistedRemoved]
   :streamtide/patrons-added-event [:streamtide-fwd :PatronsAdded]
   :streamtide/round-started-event [:streamtide-fwd :RoundStarted]
   :streamtide/round-closed-event [:streamtide-fwd :RoundClosed]
   :streamtide/matching-pool-donation-event [:streamtide-fwd :MatchingPoolDonation]
   :streamtide/matching-pool-donation-token-event [:streamtide-fwd :MatchingPoolDonationToken]
   :streamtide/distribute-event [:streamtide-fwd :Distribute]
   :streamtide/distribute-round-event [:streamtide-fwd :DistributeRound]
   :streamtide/donate-event [:streamtide-fwd :Donate]})
