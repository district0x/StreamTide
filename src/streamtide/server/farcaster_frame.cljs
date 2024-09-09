(ns streamtide.server.farcaster-frame
  (:require
    ["@hono/node-server" :refer [serve]]
    ["@hono/node-server/serve-static" :refer [serveStatic]]
    ["axios" :as axios]
    ["hono/jsx" :refer [jsx]]
    [bignumber.core :as bn]
    [district.server.config :refer [config]]
    [district.shared.async-helpers :refer [safe-go <?]]
    [mount.core :as mount :refer [defstate]]
    [shadow.esm :refer [dynamic-import]]
    [streamtide.server.db :as stdb]
    [streamtide.server.utils :refer [wrap-as-promise]]
    [taoensso.timbre :as log]))

(def TX-API "/tx")
(def FRAME-API "/frame")
(def FINISH-TX-FRAME "/finish")
(def st-contract-abi (js/JSON.parse "[{\"inputs\":[{\"internalType\":\"address[]\",\"name\":\"patronAddresses\",\"type\":\"address[]\"},{\"internalType\":\"uint256[]\",\"name\":\"amounts\",\"type\":\"uint256[]\"}],\"name\":\"donate\",\"outputs\":[],\"stateMutability\":\"payable\",\"type\":\"function\",\"payable\":true}]"))
(def DEFAULT-IMAGE "/img/layout/streamtide-farcaster.png")

(def BUTTON-PRICES
  {:1 "10"
   :2 "25"
   :3 "50"})

(defonce server-instance (atom nil))

(defn- to-jsx [hiccup]
  (let [[tag & rest] hiccup
        [props & children] (if (map? (first rest)) rest (cons nil rest))
        tag-name (if (keyword? tag) (name tag) tag)
        props (clj->js props)
        children (map (fn [child]
                        (if (vector? child)
                          (to-jsx child)
                          child))
                      children)]
    (apply jsx tag-name props children)))

(defn- get-creator [context]
  (or (-> context .-req (.param "creator")) (-> context .-req .query (js->clj :keywordize-keys true) :creator)))

(defn- get-photo [context]
  (safe-go
    (if (= "1" (-> context .-req .query (js->clj :keywordize-keys true) :profile-pic))
      (:user/photo (<? (stdb/get-user (get-creator context))))
      DEFAULT-IMAGE)))

(defn- get-tip-value [context]
  (or (.-inputText context)
      (get BUTTON-PRICES (keyword (str (.-buttonIndex context))))))

(defn- build-frames [frog opts]
  (let [Button (.-Button frog)
        TextInput (.-TextInput frog)
        TransactionButton (.-Transaction Button)
        ResetButton (.-Reset Button)
        LinkButton (.-Link Button)]
    {(str FRAME-API "/:creator")
     (fn [c]
       (wrap-as-promise
         (safe-go
           (let [creator (get-creator c)
                 image (<? (get-photo c))
                 button-value (.-buttonValue c)]
             (if (= button-value "custom-tip")
               (.res c (clj->js {:image image
                                 :imageAspectRatio "1:1"
                                 :intents (map
                                            to-jsx
                                            [[TextInput {:placeholder "Amount in $"}]
                                             [TransactionButton
                                              {:target (str TX-API "?creator=" creator)
                                               :action FINISH-TX-FRAME}
                                              "Tip"]
                                             [ResetButton "< Go Back"]])}))
               (.res c (clj->js {:image image
                                 :imageAspectRatio "1:1"
                                 :intents (map
                                            to-jsx
                                            (concat (map (fn [amount] [TransactionButton
                                                                       {:target (str TX-API "?creator=" creator)
                                                                        :action FINISH-TX-FRAME}
                                                                       (str "Tip " amount "$")]) (vals BUTTON-PRICES))
                                                    [[Button {:value "custom-tip"} "Custom tip"]]))})))))))
     FINISH-TX-FRAME
     (fn [c]
       (wrap-as-promise
         (safe-go
           (let [tx-id (.-transactionId c)
                 image (<? (get-photo c))]
             (.res c (clj->js {:image image
                               :imageAspectRatio "1:1"
                               :intents (map
                                          to-jsx
                                          [[LinkButton {:href (str (:etherscan-tx-url opts) "/" tx-id)} "See transaction status"]
                                           [ResetButton "< Restart"]])}))))))}))

(defn- dollar-to-wei [dollar-amount {:keys [:etherscan-api-key]}]
  (safe-go
    (let [result (<? (.get axios (str "https://api.etherscan.io/api?module=stats&action=ethprice&apikey=" etherscan-api-key)))
          eth-price (-> result .-data .-result .-ethusd)]
      (when-not eth-price (throw (js/Error. (str "Cannot fetch current eth price. Details: " (js/JSON.stringify (.-data result))))))
      (str (.integerValue (bn/* (bn/pow (js/BigNumber. 10) 18) (bn// (js/BigNumber. dollar-amount) (js/BigNumber. eth-price))))))))

(defn- build-transactions [opts]
  {TX-API (fn [c]
            (wrap-as-promise
              (safe-go
                (let [creator (get-creator c)
                      input-amount (get-tip-value c)
                      amount-wei (<? (dollar-to-wei input-amount opts))
                      chain-id (str "eip155:" (:chain-id opts))
                      st-address (-> @(-> @config :smart-contracts :contracts-var) :streamtide-fwd :address)]
                  (.contract c (clj->js {:abi st-contract-abi
                                         :chainId chain-id
                                         :functionName "donate"
                                         :args [[creator] [amount-wei]]
                                         :to st-address
                                         :value amount-wei}))))))})

(defn start [{:keys [:hostname :port :path :title :use-devtools? :static-public :on-error :hub :secret] :as opts}]
  (-> (js/Promise.all [(dynamic-import "frog") (when use-devtools? (dynamic-import "frog/dev"))])
      (.then (fn [[frog frog-dev]]
               (try
                 (let [Frog (.-Frog frog)
                       app (Frog. #js {:title title
                                       :basePath (or path "/")
                                       :hub hub
                                       :secret secret})]
                   (when static-public
                    (.use app "/*" (serveStatic #js {:root static-public})))

                   ; register all frames
                   (doseq [[path frame-fn] (build-frames frog opts)]
                     (.frame app path frame-fn))
                   ; register all transactions
                   (doseq [[path frame-fn] (build-transactions opts)]
                     (.transaction app path frame-fn))

                   (when use-devtools?
                    ((.-devtools frog-dev) app #js {:serveStatic serveStatic}))

                   (let [server (serve #js {:fetch (.-fetch app)
                                            :hostname hostname
                                            :port  port})]
                     (reset! server-instance server)
                     {}))
                 (catch :default e
                   (log/error "Failed load Framecaster Frame" {:error e})
                   (when on-error (on-error))))))
      (.catch (fn [error]
                (log/error "Failed to dynamic import frog" {:error error})
                (js/process.exit 1)))))


(defn stop []
  (if-let [server @server-instance]
    (.close server)))


(defstate farcaster-frame
          :start (start (merge (:farcaster-frame @config)
                               (:farcaster-frame (mount/args))))
          :stop (stop))
