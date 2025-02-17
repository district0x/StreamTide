(ns streamtide.shared.smart-contracts-qa)
  (def smart-contracts
    {:migrations {:name "Migrations" :address "0xb2B33a219a48324C3BC47aD141FE164D7F3F4535"}
     :streamtide {:name "MVPCLR" :address "0x25f9C724ebFBDab03be68B317F4ef18508Ea661a"}
     :streamtide-fwd {:name "MutableForwarder" :address "0x2D1A3e2CAec7402eBCE45c787D878F70cB504802" :forwards-to :streamtide}})
  (def multichain-smart-contracts
    {})
