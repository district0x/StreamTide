(ns streamtide.server.donations-configs.eth-donation
  (:require [cljs-web3-next.helpers :refer [zero-address]]
            [district.server.config :refer [config]]
            [district.shared.async-helpers :refer [<? safe-go]]
            [streamtide.server.donations-configs.donations-configs :as donations-configs]))

(defmethod donations-configs/verify :eth [_ _]
  (safe-go
    {:coin/address zero-address :coin/chain-id (-> @config :donations-configs :eth :chain-id)}))
