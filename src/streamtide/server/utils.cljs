(ns streamtide.server.utils
  "Utilities for server"
  (:require ["axios" :as axios]
            [bignumber.core :as bn]
            [cljs-web3-next.core :as web3]
            [cljs-web3-next.helpers :refer [zero-address]]
            [cljs.core.async :refer [<! take!]]
            [clojure.string :as str]
            [district.server.config :refer [config]]
            [district.shared.async-helpers :refer [safe-go <?]]
            [taoensso.timbre :as log]))

(defn wrap-as-promise [chanl]
      (js/Promise. (fn [resolve reject]
                     (take! (safe-go (<? chanl))
                            (fn [v-or-err#]
                              (if (cljs.core/instance? js/Error v-or-err#)
                                (reject v-or-err#)
                                (resolve v-or-err#)))))))

(defn- format-date [timestamp]
  (let [d (js/Date. (* timestamp 1000))]
    (-> d .toISOString (.split "T") first)))

(defn eth->usd-amount [wei-amount timestamp]
  (safe-go
    (try
      (let [url (str (-> @config :coin-api :eth-price-url (str/replace "__DATE__" (format-date timestamp))))
            response (<? (.get axios url))
            price (-> (reduce #(aget %1 %2) (.-data response) (-> @config :coin-api :eth-price-response-path)) js/parseFloat)
            eth-amount (-> wei-amount (web3/from-wei :ether) js/parseFloat)]
        (* price eth-amount))
      (catch :default e#
        (log/error "Failed to fetch usd amount" (merge {:error e#}
                                                       (ex-data e#)
                                                       {:wei-amount wei-amount
                                                        :timestamp timestamp}))
        nil))))

(defn- api-coin-id [token-addr]
  (safe-go
    (let [url (str (-> @config :coin-api :coin-id-url (str/replace "__TOKEN_ADDRESS__" token-addr)))
          response (<? (.get axios url))]
      (reduce #(aget %1 %2) (.-data response) (-> @config :coin-api :coin-id-response-path)))))

(defn token->wei-usd-amount [amount token-addr timestamp decimals]
  (safe-go
    (if (= token-addr zero-address)
      {:amount-wei amount
       :amount-usd (<? (eth->usd-amount amount timestamp))}
      (try
        (let [coin-id (<? (api-coin-id token-addr))
              url (str (-> @config :coin-api :token-price-url (str/replace "__DATE__" (format-date timestamp)) (str/replace "__COIN_ID__" coin-id)))
              response (<? (.get axios url))
              eth-price (reduce #(aget %1 %2) (.-data response) (-> @config :coin-api :token-price-eth-response-path))
              usd-price (reduce #(aget %1 %2) (.-data response) (-> @config :coin-api :token-price-usd-response-path))
              amount-w-decimals (bn// (js/BigNumber. amount) (bn/pow (js/BigNumber. 10) decimals))]
          {:amount-wei (web3/to-wei (bn/fixed (bn/* (js/BigNumber. eth-price) amount-w-decimals) 18) :ether)
           :amount-usd (bn/fixed (bn/* (js/BigNumber. usd-price) amount-w-decimals) 2)})
        (catch :default e#
          (log/error "Failed to fetch amounts" (merge {:error e#}
                                                         (ex-data e#)
                                                         {:amount amount
                                                          :token-addr token-addr
                                                          :timestamp timestamp}))
          {:amount-wei nil
           :amount-usd nil})))))