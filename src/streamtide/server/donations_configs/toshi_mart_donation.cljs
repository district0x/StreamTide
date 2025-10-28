(ns streamtide.server.donations-configs.toshi-mart-donation
  (:require
    ["axios" :as axios]
    [cljs-web3-next.core :as web3-next]
    [cljs-web3-next.eth :as web3-eth]
    [cljs-web3-next.helpers :refer [zero-address]]
    [district.server.config :refer [config]]
    [district.server.smart-contracts :as smart-contracts]
    [district.server.web3 :refer [web3]]
    [district.shared.async-helpers :refer [<? safe-go]]
    [streamtide.server.donations-configs.donations-configs :as donations-configs]
    [taoensso.timbre :as log]))

(def abi-reduced-portal (js/JSON.parse "[{\"inputs\":[{\"components\":[{\"internalType\":\"address\",\"name\":\"inputToken\",\"type\":\"address\"},{\"internalType\":\"address\",\"name\":\"outputToken\",\"type\":\"address\"},{\"internalType\":\"uint256\",\"name\":\"inputAmount\",\"type\":\"uint256\"}],\"internalType\":\"struct DummyQuoteContract.QuoteExactInputParams\",\"name\":\"params\",\"type\":\"tuple\"}],\"name\":\"quoteExactInput\",\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"outputAmount\",\"type\":\"uint256\"}],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}]"))

(def abi-reduced-token (js/JSON.parse "[{\"inputs\":[],\"name\":\"name\",\"outputs\":[{\"internalType\":\"string\",\"name\":\"\",\"type\":\"string\"}],\"stateMutability\":\"pure\",\"type\":\"function\",\"constant\":true},{\"inputs\":[],\"name\":\"symbol\",\"outputs\":[{\"internalType\":\"string\",\"name\":\"\",\"type\":\"string\"}],\"stateMutability\":\"pure\",\"type\":\"function\",\"constant\":true},{\"inputs\":[],\"name\":\"decimals\",\"outputs\":[{\"internalType\":\"uint8\",\"name\":\"\",\"type\":\"uint8\"}],\"stateMutability\":\"view\",\"type\":\"function\",\"constant\":true},{\"inputs\":[],\"name\":\"metaURI\",\"outputs\":[{\"internalType\":\"string\",\"name\":\"\",\"type\":\"string\"}],\"stateMutability\":\"pure\",\"type\":\"function\",\"constant\":true}]"))

(defn get-ipfs-data [cid]
  (safe-go
    (let [url (str (-> @config :donations-configs :toshi-mart :ipfs-gateway) cid)
          response (<? (.get axios url))]
      (.-data response))))

(defn get-image-url [token-contract]
  (safe-go
    (try
      (let [metadata-cid (<? (smart-contracts/contract-call token-contract "metaURI"))
            metadata (<? (get-ipfs-data metadata-cid))
            ipfs-gateway (-> @config :donations-configs :toshi-mart :ipfs-gateway)
            image-cid (aget metadata "image" )]
        (str ipfs-gateway image-cid))
      (catch :default e#
        (log/error "Failed to fetch token image" (merge {:error e#}
                                                        (ex-data e#)
                                                        {:token-addr token-contract}))
             nil))))

(defn verify [coin-address]
  (safe-go
    (when-not (web3-next/address? coin-address)
      (throw (js/Error. (str "invalid coin address: " coin-address))))
    (let [portal-address (-> @config :donations-configs :toshi-mart :portal-address)
          portal-contract (web3-eth/contract-at @web3 abi-reduced-portal portal-address)
          params [zero-address coin-address 1]
          _ (<? (smart-contracts/contract-call portal-contract "quoteExactInput" [params])) ; this will throw an error if contract is not registered in Toshi Mart
          token-contract (web3-eth/contract-at @web3 abi-reduced-token coin-address)
          image-url (<? (get-image-url token-contract))
          name (<? (smart-contracts/contract-call token-contract "name"))
          symbol (<? (smart-contracts/contract-call token-contract "symbol"))
          decimals (<? (smart-contracts/contract-call token-contract "decimals"))]
      {:coin/address coin-address
       :coin/chain-id (-> @config :donations-configs :toshi-mart :chain-id)
       :coin/name name
       :coin/symbol symbol
       :coin/decimals decimals
       :coin/type :erc20
       :coin/image-url image-url})))

(defn parse-call-data [gained token]
  (safe-go
    {:coin token
     :amount gained}))

(defmethod donations-configs/verify :toshi-mart [_ coin-address]
  (verify coin-address))

(defmethod donations-configs/parse-call-data :toshi-mart [_ {:keys [:target :call-data :gained :token] :as _args}]
  (parse-call-data gained token))
