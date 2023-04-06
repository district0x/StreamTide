(ns streamtide.server.verifiers.eth-verifier
  (:require
    [cljs-web3-next.core :as web3-next]
    [cljs-web3-next.eth :as web3-eth]
    [cljs-web3-next.utils :as web3-utils]
    [district.server.config :refer [config]]
    [district.shared.async-helpers :refer [<? safe-go]]))


(defn- verify-balance [{:keys [:web3 :min-balance] :as args} user-address]
  (safe-go
    (let [web3-provider (web3-next/create-web3 nil web3)
          balance (<? (web3-eth/get-balance web3-provider user-address))
          balance-eth (js/parseFloat (web3-utils/from-wei web3-provider balance "ether"))
          min-balance (js/parseFloat min-balance)]
      (if (>= balance-eth min-balance)
        {:valid? true
         :url user-address}
        {:valid? false}))))

(defn verify [user-address]
  (let [verifier-config (-> @config :verifiers :eth)]
    (safe-go
      (loop [chain-configs verifier-config]
        (when chain-configs
          (let [verifier (<? (verify-balance (second (first chain-configs)) user-address))]
            (if (:valid? verifier)
              verifier
              (recur (next chain-configs)))))))))
