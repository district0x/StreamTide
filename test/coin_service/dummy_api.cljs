(ns coin-service.dummy-api
  (:require [cljs.nodejs :as nodejs]
            [cljs.reader :refer [read-string]]))

(nodejs/enable-util-print!)

(def http (nodejs/require "http"))
(def url (nodejs/require "url"))

(defn parse-query [req]
  (let [parsed (.parse url (.-url req) true)]
    (.-query parsed)))

(defn handler [req res]
  (let [method (.-method req)
        pathname (.parse url (.-url req) true)
        path (.-pathname pathname)
        query (.-query pathname)]
    (js/console.log "Request:" method path query)
    (if (= method "GET")
      (cond
        ;; GET /eth_price?date=__
        (= path "/eth_price")
        (do
          (.writeHead res 200 #js {"Content-Type" "application/json"})
          (.end res (js/JSON.stringify #js {:market_data #js {:current_price #js {:usd "3899.123"}}})))

        ;; GET /coin_id?token_address=__
        (= path "/coin_id")
        (do
          (.writeHead res 200 #js {"Content-Type" "application/json"})
          (.end res (js/JSON.stringify #js {:id "tstcoin"})))

        ;; GET /coin_price?coin_id=__&date=__
        (= path "/coin_price")
        (do
          (.writeHead res 200 #js {"Content-Type" "application/json"})
          (.end res (js/JSON.stringify #js {:market_data #js {:current_price #js {:usd "25.445" :eth "0.00652583"}}})))

        ;; fallback 404
        :else
        (do
          (.writeHead res 404 #js {"Content-Type" "application/json"})
          (.end res (js/JSON.stringify #js {:error "Not found"}))))
      ;; fallback for non-GET
      (do
        (.writeHead res 405 #js {"Content-Type" "application/json"})
        (.end res (js/JSON.stringify #js {:error "Method not allowed"}))))))

(defn -main []
  (let [port 4000
        server (.createServer http handler)]
    (.listen server port #(js/console.log (str "Dummy Coin Service running at http://localhost:" port)))))

(set! *main-cli-fn* -main)
