(ns streamtide.shared.smart-contracts-qa)
  (def smart-contracts
    {:migrations {:name "Migrations" :address "0xb2B33a219a48324C3BC47aD141FE164D7F3F4535"}
     :streamtide {:name "MVPCLR" :address "0x25f9C724ebFBDab03be68B317F4ef18508Ea661a"}
     :streamtide-fwd {:name "MutableForwarder" :address "0x2D1A3e2CAec7402eBCE45c787D878F70cB504802" :forwards-to :streamtide}})
  (def multichain-smart-contracts
    {:11155111 {:matching-pool {:name "MatchingPool" :address "0xa7f3c5c60f65b55469468a6212b16c813b44701a"}
                :matching-pool-fwd {:name "MutableForwarder" :address "0xda35804a3ad0A27706118D09acD47C775E52b884" :forwards-to :matching-pool}}})
