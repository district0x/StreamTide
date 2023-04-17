(ns streamtide.shared.smart-contracts-dev)
(def smart-contracts
  {:migrations {:name "Migrations" :address "0x641E3c77eC2582999b8f6de0AF9BF1D465Fd4CB2"}
   :streamtide {:name "Streamtide" :address "0xAF61d3cA9f88E472372c76fb04Bc47cc79b4A87A"}
   :streamtide-fwd {:name "MutableForwarder" :address "0x0b982d6CfA88Cd81143b1E76671ABDF3a453bc40" :forwards-to :streamtide}})
