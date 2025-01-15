(ns streamtide.server.sidechain-syncer
  "Populates the DB from the events coming from a sidechain"
  (:require
    [district.server.config :refer [config]]
    [mount.core :as mount :refer [defstate]]
    [streamtide.server.syncer :as syncer]))


(declare start)
(declare stop)

(defstate ^{:on-reload :noop} sidechain-syncer
          :start (start (merge (:sidechain-syncer @config)
                               (:sidechain-syncer (mount/args))))
          :stop (stop sidechain-syncer))

(defn start [{:keys [:disabled? :reload-interval] :as opts}]
  (syncer/register-events opts {:matching-pool/matching-pool-donation-event syncer/matching-pool-donation-event
                                :matching-pool/matching-pool-donation-token-event syncer/matching-pool-donation-token-event
                                :matching-pool/distribute-event syncer/distribute-event
                                :matching-pool/distribute-round-event syncer/distribute-round-event}))

(defn stop [sidechain-syncer]
  (syncer/stop sidechain-syncer))
