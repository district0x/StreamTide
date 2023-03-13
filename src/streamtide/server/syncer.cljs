(ns streamtide.server.syncer
  "Populates the DB from the events coming from the blockchain"
  (:require
    [streamtide.server.utils :as server-utils]
    [district.shared.async-helpers :refer [<? safe-go]]
    [district.server.web3-events :as web3-events]
    [district.time :as time]
    [district.server.config :refer [config]]
    [mount.core :as mount :refer [defstate]]
    [taoensso.timbre :as log]))


(declare start)
(declare stop)

(defstate ^{:on-reload :noop} syncer
          :start (start (merge (:syncer @config)
                               (:syncer (mount/args))))
          :stop (stop syncer))

(defn dispatcher [callback]
  ; TODO
  )

(defn start [opts]
  ;(safe-go
  ;  (when-not (:disabled? opts)
  ;    (when-not (web3-eth/is-listening? @web3)
  ;      (throw (js/Error. "Can't connect to Ethereum node")))
  ;    (when-not (= ::db/started @db/streamtide-db)
  ;      (throw (js/Error. "Database module has not started")))
  ;    (let [start-time (server-utils/now)
  ;          event-callbacks {
  ;                           ; TODO
  ;                           }
  ;          callback-ids (doall (for [[event-key callback] event-callbacks]
  ;                                (web3-events/register-callback! event-key (dispatcher callback))))]
  ;      (web3-events/register-after-past-events-dispatched-callback! (fn []
  ;                                                                     (log/warn "Syncing past events finished" (time/time-units (- (server-utils/now) start-time)) ::start)))
  ;      (assoc opts :callback-ids callback-ids))))
  )

(defn stop [syncer]
  ;(web3-events/unregister-callbacks! (:callback-ids @syncer))
  )
