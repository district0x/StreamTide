(ns streamtide.shared.smart-contracts-prod)
  (def smart-contracts
    {:migrations {:name "Migrations" :address "0x5f5391A0ec248BBC7906242Ec9D10394158Fd70b"} :streamtide {:name "MVPCLR" :address "0x89Cf0c5f93189642911412D2d49E4b872689F44e"} :streamtide-fwd {:name "MutableForwarder" :address "0x6Db2844F211580ae950Ed10635AA12409Ee816De" :forwards-to :streamtide}})
