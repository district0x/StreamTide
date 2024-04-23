(ns streamtide.shared.smart-contracts-qa)
  (def smart-contracts
    {:migrations {:name "Migrations" :address "0x3616f333628b61CEbfddCfbe001A90c4C8feEF02"}
     :streamtide {:name "MVPCLR" :address "0x3b038Bb1fdB38CF7CC19c7917D6979A1f5454897"}
     :streamtide-fwd {:name "MutableForwarder" :address "0x26561312D23e9AfF9FbB6387D8911729705C56e3" :forwards-to :streamtide}})
