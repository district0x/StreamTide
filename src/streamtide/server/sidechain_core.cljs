(ns streamtide.server.sidechain-core
  "Entry point for listening to events of side chains to update matching pools.
  Reads the config and starts only modules required to run the syncer"
  (:require [clojure.string :as str]
            [district.server.config :as district.server.config]
            [district.server.db-async :as district.server.db]
            [district.server.logging :as district.server.logging]
            [district.server.smart-contracts :as district.server.smart-contracts]
            [district.shared.async-helpers :as async-helpers]
            [mount.core :as mount]
            [streamtide.server.constants :as constants]
            [streamtide.server.db :as streamtide.server.db]
            [streamtide.shared.smart-contracts-dev :as smart-contracts-dev]
            [streamtide.shared.smart-contracts-prod :as smart-contracts-prod]
            [streamtide.shared.smart-contracts-qa :as smart-contracts-qa]
            [streamtide.server.farcaster-frame]
            [streamtide.server.sidechain-syncer :as streamtide.server.sidechain-syncer]
            [taoensso.timbre :as log :refer [info warn error]])
  (:require-macros [streamtide.shared.utils :refer [get-environment]]))

(defonce resync-count (atom 0))

(when (not= 3 (.-length js/process.argv))
  (pr "Expected at least one argument")
  (js/process.exit 1))

(def chain-id
  (aget js/process.argv 2))

(def contracts-var
  (condp = (get-environment)
    "prod" (atom ((keyword chain-id) smart-contracts-prod/multichain-smart-contracts))
    "qa" (atom ((keyword chain-id) smart-contracts-qa/multichain-smart-contracts))
    "dev" (atom ((keyword chain-id) smart-contracts-dev/multichain-smart-contracts))))

(defn start []
  (-> (mount/only #{#'district.server.config/config
                    #'district.server.db/db
                    #'district.server.logging/logging
                    #'district.server.smart-contracts/smart-contracts
                    #'district.server.web3-events/web3-events
                    #'district.server.web3/web3
                    #'streamtide.server.db/streamtide-db
                    #'streamtide.server.sidechain-syncer/sidechain-syncer})
      (mount/with-args
        {:config {:file-path (str "config-" chain-id ".edn")
                  :default {:logging {:level "info"
                                      :console? false}
                            :time-source :js-date
                            :db {:transform-result-keys-fn (comp keyword demunge #(str/replace % #"_slash_" "_SLASH_"))}
                            :web3 {:url "ws://127.0.0.1:8546"
                                   :on-offline (fn []
                                                 (log/warn "Ethereum node went offline, stopping syncing modules" {:resyncs @resync-count} ::web3-watcher)
                                                 (mount/stop #'district.server.web3-events/web3-events
                                                             #'streamtide.server.sidechain-syncer/sidechain-syncer))
                                   :on-online (fn []
                                                (log/warn "Ethereum node went online again, starting syncing modules" {:resyncs (swap! resync-count inc)} ::web3-watcher)
                                                (mount/start #'district.server.web3-events/web3-events
                                                             #'streamtide.server.sidechain-syncer/sidechain-syncer))}
                            :syncer {:reload-interval 7200000}
                            :smart-contracts {:contracts-var contracts-var}
                            :web3-events {:events constants/web3-matching-pool-events
                                          :on-error #(js/process.exit 1)}}}})
      (mount/start)
      (as-> $ (log/info "Started v1.0.0" {:components $
                                          :smart-contracts-qa smart-contracts-qa/smart-contracts
                                          :smart-contracts-prod smart-contracts-prod/smart-contracts
                                          :config @district.server.config/config}))))

(defn stop []
  (mount/stop))

(defn restart []
  (stop)
  (start))

(defn -main [& _]
  (async-helpers/extend-promises-as-channels!)
  (.on js/process "unhandledRejection"
       (fn [reason _] (log/error "Unhandled promise rejection " {:reason reason})))
  (start))

(set! *main-cli-fn* -main)