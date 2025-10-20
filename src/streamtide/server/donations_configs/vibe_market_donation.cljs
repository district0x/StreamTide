(ns streamtide.server.donations-configs.vibe-market-donation
  (:require
    ["axios" :as axios]
    [cljs-web3-next.core :as web3-next]
    [district.server.config :refer [config]]
    [district.shared.async-helpers :refer [<? safe-go]]
    [streamtide.server.donations-configs.donations-configs :as donations-configs]))


(defn verify [drop-address]
  (safe-go
    (when-not (web3-next/address? drop-address)
      (throw (js/Error. (str "invalid drop address: " drop-address))))
    (let [vive-market-api-config (-> @config :donations-configs :vibe-market)
          api-key (:api-key vive-market-api-config)
          url (str "https://build.wield.xyz/vibe/boosterbox/contractAddress/" drop-address)
          options (clj->js {:headers {"API-KEY" api-key}
                            :validateStatus (fn [_] true)})
          response (<? (.get axios url options))
          status (.-status response)
          data (.-data response)]
      (when-not (and (= status 200) (.-success data))
        (throw (js/Error. "Cannot get contract info."))))))

(defmethod donations-configs/verify :vibe-market [_ {:keys [:user/vibe-market-drop-address]}]
  (verify vibe-market-drop-address))
