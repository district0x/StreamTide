(ns streamtide.ui.config
  "UI Config"
  (:require [mount.core :refer [defstate]]
            [streamtide.shared.smart-contracts-dev :as smart-contracts-dev]
            [streamtide.shared.smart-contracts-prod :as smart-contracts-prod]
            [streamtide.shared.smart-contracts-qa :as smart-contracts-qa])
  (:require-macros [streamtide.shared.utils :refer [get-environment]]))


(def development-config
  {:debug? true
   :logging {:level :debug
             :console? true}
   :graphql {:url "http://localhost:6300/graphql"}
   :smart-contracts {:contracts smart-contracts-dev/smart-contracts}
   :verifiers {:discord {:client-id "1135876901093781544"}}
   :web3-chain {:chain-id "1337"
                :rpc-urls ["http://localhost:8545"]
                :chain-name "Ganache"
                :native-currency {:name "ETH"
                                  :symbol "ETH"
                                  :decimals 18}}
   :notifiers {:web-push {:public-key "BGtkUrXx0vlsFpfmf8rDNqswKAlrSUQUE8xN4Jf6F3rtQCpbdR-vakwnUnhnVWYl1kdfUXzjfNini19ZyGVtaMM"}}
   :thirdweb {:client-id "."}
   })

(def qa-config
  {:logging {:level :warn
             :console? true}
   :graphql {:url "https://api.streamtide.qa.district0x.io/graphql"}
   :smart-contracts {:contracts smart-contracts-qa/smart-contracts}
   :verifiers {:discord {:client-id "1135876901093781544"}}
   :web3-chain {:chain-id "84532"
                :rpc-urls ["https://sepolia.base.org"]
                :chain-name "Base Sepolia"
                :native-currency {:name "ETH"
                                  :symbol "ETH"
                                  :decimals 18}
                :block-explorer-urls ["https://sepolia-explorer.base.org"]}
   :notifiers {:web-push {:public-key "BGtkUrXx0vlsFpfmf8rDNqswKAlrSUQUE8xN4Jf6F3rtQCpbdR-vakwnUnhnVWYl1kdfUXzjfNini19ZyGVtaMM"}}
   :thirdweb {:client-id "f478f4123340f16303e57df57b6e26ef"}
   })

(def production-config
  {:logging {:level :warn
             :console? false}
   :graphql {:url "https://api.streamtide.io/graphql"}
   :smart-contracts {:contracts smart-contracts-prod/smart-contracts}
   :verifiers {:discord {:client-id "1135876901093781544"}}
   :web3-chain {:chain-id "8453"
                :rpc-urls ["https://mainnet.base.org"]
                :chain-name "Base Mainnet"
                :native-currency {:name "ETH"
                                  :symbol "ETH"
                                  :decimals 18}
                :block-explorer-urls ["https://base.blockscout.com"]}
   :notifiers {:web-push {:public-key "BGtkUrXx0vlsFpfmf8rDNqswKAlrSUQUE8xN4Jf6F3rtQCpbdR-vakwnUnhnVWYl1kdfUXzjfNini19ZyGVtaMM"}}
   :thirdweb {:client-id "f478f4123340f16303e57df57b6e26ef"}
   })

(def config-map
  (condp = (get-environment)
    "prod" production-config
    "qa" qa-config
    "dev" development-config))

(defn start []
  )

(defstate config
          :start (start)
          :stop ::stopped)
