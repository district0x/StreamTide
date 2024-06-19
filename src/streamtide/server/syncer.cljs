(ns streamtide.server.syncer
  "Populates the DB from the events coming from the blockchain"
  (:require
    [bignumber.core :as bn]
    [camel-snake-kebab.core :as camel-snake-kebab]
    [cljsjs.bignumber]
    [cljs-web3-next.core :as web3-core]
    [cljs-web3-next.eth :as web3-eth]
    [cljs-web3-next.helpers :refer [zero-address]]
    [cljs.core.async :as async :refer [<! go]]
    [cljs.core.async.impl.protocols :refer [ReadPort]]
    [clojure.string :as string]
    [district.shared.async-helpers :refer [<? safe-go]]
    [district.server.smart-contracts :as smart-contracts]
    [district.server.web3-events :as web3-events]
    [district.time :as time]
    [district.server.config :refer [config]]
    [district.server.web3 :refer [ping-start ping-stop web3]]
    [mount.core :as mount :refer [defstate]]
    [streamtide.server.notifiers.notifiers :as notifiers]
    [streamtide.server.db :as db]
    [streamtide.shared.utils :as shared-utils :refer [abi-reduced-erc20]]
    [taoensso.timbre :as log]))


(declare start)
(declare stop)

(defonce reload-timeout (atom nil))
(defonce last-block-number (atom -1))

(defstate ^{:on-reload :noop} syncer
          :start (start (merge (:syncer @config)
                               (:syncer (mount/args))))
          :stop (stop syncer))

(defn- get-event [{:keys [:event :log-index :block-number]
                   {:keys [:contract-key :forwards-to]} :contract}]
  {:event/contract-key (name (or forwards-to contract-key))
   :event/event-name (name event)
   :event/log-index log-index
   :event/block-number block-number})

(defn- block-timestamp* [block-number]
  (let [out-ch (async/promise-chan)]
    (smart-contracts/wait-for-block block-number (fn [error result]
                                                   (if error
                                                     (async/put! out-ch error)
                                                     (let [{:keys [:timestamp]} (js->clj result :keywordize-keys true)]
                                                       (log/debug "cache miss for block-timestamp" {:block-number block-number
                                                                                                    :timestamp timestamp})
                                                       (async/put! out-ch timestamp)))))
    out-ch))


;;;;;;;;;;;;;;;;;;;;
;; Event handlers ;;
;;;;;;;;;;;;;;;;;;;;

(defn admin-added-event [_ {:keys [:args]}]
  (let [{:keys [:_admin]} args]
    (safe-go
      (<! (db/upsert-user-info! {:user/address _admin}))
      (db/add-role _admin :role/admin))))

(defn admin-removed-event [_ {:keys [:args]}]
  (let [{:keys [:_admin]} args]
    (safe-go
      (<! (db/remove-role _admin :role/admin)))))

(defn blacklisted-added-event [_ {:keys [:args]}]
  (let [{:keys [:_blacklisted]} args]
    (safe-go
      (<! (db/upsert-user-info! {:user/address _blacklisted}))
      (<! (db/add-to-blacklist! {:user/address _blacklisted})))))

(defn blacklisted-removed-event [_ {:keys [:args]}]
  (let [{:keys [:_blacklisted]} args]
    (safe-go
      (<! (db/upsert-user-info! {:user/address _blacklisted}))
      (<! (db/remove-from-blacklist! {:user/address _blacklisted})))))

(defn patrons-added-event [_ {:keys [:args]}]
  (let [{:keys [:addresses :timestamp]} args
        addresses (js->clj addresses)]
    (safe-go
      (<! (db/ensure-users-exist! addresses))
      (let [grants {:user/addresses addresses
                    :grant/status (name :grant.status/approved)
                    :grant/decision-date timestamp}]
        (<! (db/upsert-grants! grants))
        (<! (notifiers/notify-grants-statuses grants))))))

(defn round-started-event [_ {:keys [:args]}]
  (let [{:keys [:round-start :round-id :round-duration]} args]
    (safe-go
      (<! (db/add-round! {:round/id round-id
                      :round/start round-start
                      :round/duration round-duration
                      :round/matching-pool 0
                      :round/distributed 0})))))

(defn current-round []
  (first (:items (<! (db/get-rounds {:first 1 :order-by :rounds.order-by/id :order-dir :desc})))))

(defn round-closed-event [_ {:keys [:args]}]
  (let [{:keys [:round-id :timestamp]} args]
    (safe-go
      (let [round (<! (db/get-round round-id))]
        (when (shared-utils/active-round? round timestamp)
          (<! (db/update-round! {:round/id round-id
                             :round/duration (- timestamp (:round/start round))})))))))

(defn donate-event [_ {:keys [:args]}]
  (let [{:keys [:sender :value :patron-address :round-id :timestamp]} args]
    (safe-go
      (let [round-id (when (not= (str round-id) "0") round-id)
            donation {:donation/sender sender
                      :donation/receiver patron-address
                      :donation/date timestamp
                      :donation/amount value
                      :donation/coin zero-address
                      :round/id round-id}]
        (<! (db/upsert-user-info! {:user/address sender}))
        (<! (db/add-donation! donation))
        (<! (notifiers/notify-donation donation))
        (let [min-donation (:user/min-donation (db/get-user patron-address))]
          (when (or (nil? min-donation) (bn/>= (js/BigNumber. value) (js/BigNumber. min-donation)))
            (<! (db/add-user-content-permission! {:user/source-user sender
                                              :user/target-user patron-address}))))))))

(defn update-matching-pool [{:keys [:value :round-id :token]}]
  (go
    (when (bn/> (js/BigNumber. value) (js/BigNumber. 0))
      (let [matching-pool (<! (db/get-matching-pool round-id token))
            amount (bn/+ (js/BigNumber. (or (:matching-pool/amount matching-pool) 0)) (js/BigNumber. value))]
        (<! (db/upsert-matching-pool!
              {:round/id round-id
               :matching-pool/coin (string/lower-case token)
               :matching-pool/amount (bn/fixed amount)}))))))

(defn nullify-err [v]
  (if (cljs.core/instance? js/Error v) nil v))

(defn fetch-coin-info [coin-address]
  (safe-go
    (let [contract (web3-eth/contract-at @web3 abi-reduced-erc20 coin-address)
          decimals (<! (smart-contracts/contract-call contract "decimals"))
          symbol (nullify-err (<! (smart-contracts/contract-call contract "symbol")))
          name (nullify-err (<! (smart-contracts/contract-call contract "name")))]
      {:coin/address (string/lower-case coin-address)
       :coin/symbol symbol
       :coin/name name
       :coin/decimals decimals})))

(defn ensure-coin-exists! [coin-address]
  (safe-go
    (let [coin (<! (db/get-coin coin-address))]
      (when-not (:coin/address coin)
        (let [coin-info (<? (fetch-coin-info coin-address))]
          (<! (db/add-coin! coin-info)))))))

(defn matching-pool-donation-token-event [_ {:keys [:args]}]
  (let [{:keys [:sender :value :round-id :token]} args]
    (safe-go
      (do
        (<? (ensure-coin-exists! (string/lower-case token)))
        (<? (update-matching-pool (update args :token string/lower-case)))))))

(defn matching-pool-donation-event [_ {:keys [:args]}]
  (let [{:keys [:sender :value :round-id]} args]
    (safe-go
      (<? (update-matching-pool (merge args {:token zero-address}))))))

(defn distribute-event [_ {:keys [:args]}]
  (let [{:keys [:to :amount :timestamp :round-id :token]} args]
    (safe-go
      (<! (db/ensure-users-exist! [to]))
      (<! (db/add-matching! {:matching/receiver to
                             :matching/amount amount
                             :matching/date timestamp
                             :matching/coin (string/lower-case token)
                             :round/id round-id})))))

(defn distribute-round-event [_ {:keys [:args]}]
  (let [{:keys [:round-id :amount :token]} args]
    (safe-go
      (let [matching-pool (<! (db/get-matching-pool round-id token))
            distributed-amount (bn/+ (js/BigNumber. (or (:matching-pool/distributed matching-pool) 0)) (js/BigNumber. amount))]
        (<! (db/upsert-matching-pool!
              {:round/id round-id
               :matching-pool/coin (string/lower-case token)
               :matching-pool/distributed (bn/fixed distributed-amount)}))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; End of events handlers ::
;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def block-timestamp
  (memoize block-timestamp*))

(defn- dispatcher [callback]
  (fn [err {:keys [:block-number] :as event}]
    (if-not event ; event is false when websocket is disconnected
      (log/debug (str "Skipping event. error:" err))
      (safe-go
        (try
          (when-not (= (<? (db/wait-until-ready 10000)) :db/ready)
            (throw (js/Error. "Database module has not started")))
          (swap! last-block-number #(max %1 block-number))
          (let [block-timestamp (<? (block-timestamp block-number))
                event (-> event
                          (update :event camel-snake-kebab/->kebab-case)
                          (update-in [:args :version] bn/number)
                          (update-in [:args :timestamp] (fn [timestamp]
                                                          (if timestamp
                                                            (bn/number timestamp)
                                                            block-timestamp))))
                {:keys [:event/contract-key :event/event-name :event/block-number :event/log-index]} (get-event event)
                {:keys [:event/last-block-number :event/last-log-index :event/count]
                 :or {last-block-number -1
                      last-log-index -1
                      count 0}} (<? (db/get-last-event contract-key event-name))
                evt {:event/contract-key contract-key
                     :event/event-name event-name
                     :event/count count
                     :last-block-number last-block-number
                     :last-log-index last-log-index
                     :block-number block-number
                     :log-index log-index}]
            (log/debug "Handling new event" evt)
            (if (or (> block-number last-block-number)
                    (and (= block-number last-block-number) (> log-index last-log-index)))
              (let [result (callback err event)]
                ;; block if we need
                (when (satisfies? ReadPort result)
                  (<? result))
                (log/info "Handled new event" evt)
                (<? (db/upsert-event! {:event/last-log-index log-index
                                   :event/last-block-number block-number
                                   :event/count (inc count)
                                   :event/event-name event-name
                                   :event/contract-key contract-key})))

              (log/info "Skipping handling of a persisted event" evt)))
          (catch js/Error error
            (log/error "Exception when handling event" {:error error
                                                        :event event})
            ;; So we crash as fast as we can and don't drag errors that are harder to debug
            (js/process.exit 1)))))))

(defn- reload-handler [interval]
  (js/setInterval
    (fn []
      (go
        (let [connected? (true? (<! (web3-eth/is-listening? @web3)))]
          (when connected?
            (do
              (log/debug (str "disconnecting from provider to force reload. Last block: " @last-block-number))
              (web3-core/disconnect @web3))))))
    interval))

(defn reload-timeout-start [{:keys [:reload-interval]}]
  (reset! reload-timeout (reload-handler reload-interval)))

(defn reload-timeout-stop []
  (js/clearInterval @reload-timeout))

(defn start [opts]
  (when-not (:disabled? opts)
    (let [start-time (shared-utils/now)
          event-callbacks {:streamtide/admin-added-event admin-added-event
                           :streamtide/admin-removed-event admin-removed-event
                           :streamtide/blacklisted-added-event blacklisted-added-event
                           :streamtide/blacklisted-removed-event blacklisted-removed-event
                           :streamtide/patrons-added-event patrons-added-event
                           :streamtide/round-started-event round-started-event
                           :streamtide/round-closed-event round-closed-event
                           :streamtide/matching-pool-donation-event matching-pool-donation-event
                           :streamtide/matching-pool-donation-token-event matching-pool-donation-token-event
                           :streamtide/distribute-event distribute-event
                           :streamtide/distribute-round-event distribute-round-event
                           :streamtide/donate-event donate-event}
          callback-ids (doall (for [[event-key callback] event-callbacks]
                                (web3-events/register-callback! event-key (dispatcher callback))))]
      (web3-events/register-after-past-events-dispatched-callback! (fn []
                                                                     (log/warn "Syncing past events finished" (time/time-units (- (shared-utils/now) start-time)) ::start)
                                                                     (ping-start {:ping-interval 10000})
                                                                     (when (> (:reload-interval opts) 0)
                                                                       (reload-timeout-start (select-keys opts [:reload-interval])))))
      (assoc opts :callback-ids callback-ids))))

(defn stop [syncer]
  (reload-timeout-stop)
  (ping-stop)
  (web3-events/unregister-callbacks! (:callback-ids @syncer)))
