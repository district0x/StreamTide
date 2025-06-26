(ns streamtide.shared.smart-contracts-dev)
  (def smart-contracts
    {:migrations {:name "Migrations" :address "0x29e669154Cb6E916492333c66888614D3871C9f1"}
     :streamtide {:name "MVPCLR" :address "0x011F1698Da63C94887DB950d894ea78951e40c03"}
     :streamtide-fwd {:name "MutableForwarder" :address "0x00befa8671b2B7ED8fF5CdA9d608C56855DB11AA" :forwards-to :streamtide}})
  (def multichain-smart-contracts
    {:5777 {:matching-pool {:name "MatchingPool" :address "0xd1feC130e29B1c1509FBB236c61d1E306bA9dCaE"}
            :matching-pool-fwd {:name "MutableForwarder" :address "0x6AA23F1F36D45222E33294a5c935e52b1897ee2C" :forwards-to :matching-pool}}})
