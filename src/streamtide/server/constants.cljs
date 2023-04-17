(ns streamtide.server.constants)

(def web3-events
  {:streamtide/admin-added-event [:streamtide-fwd :AdminAdded]
   :streamtide/admin-removed-event [:streamtide-fwd :AdminRemoved]
   :streamtide/blacklisted-added-event [:streamtide-fwd :BlacklistedAdded]
   :streamtide/blacklisted-removed-event [:streamtide-fwd :BlacklistedRemoved]
   :streamtide/patron-added-event [:streamtide-fwd :PatronAdded]
   :streamtide/round-started-event [:streamtide-fwd :RoundStarted]
   :streamtide/matching-pool-donation-event [:streamtide-fwd :MatchingPoolDonation]
   :streamtide/distribute-event [:streamtide-fwd :Distribute]
   :streamtide/donate-event [:streamtide-fwd :Donate]
   :streamtide/failed-distribute-event [:streamtide-fwd :FailedDistribute]})
