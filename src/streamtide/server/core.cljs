(ns streamtide.server.core
  "Main entry point of the server. Reads the config and starts all modules with mount"
  (:require [cljs.nodejs :as nodejs]
            [clojure.string :as str]
            [district.graphql-utils :as graphql-utils]
            [district.server.config :as district.server.config]
            [district.server.db-async :as district.server.db]
            [district.server.graphql :as district.server.graphql]
            [district.server.graphql.utils :as graph-utils]
            [district.server.logging :as district.server.logging]
            [district.server.middleware.logging :refer [logging-middlewares]]
            [district.server.smart-contracts :as district.server.smart-contracts]
            [district.shared.async-helpers :as async-helpers]
            [mount.core :as mount]
            [streamtide.server.constants :as constants]
            [streamtide.server.db :as streamtide.server.db]
            [streamtide.server.graphql.graphql-resolvers :refer [resolvers-map]]
            [streamtide.server.graphql.middlewares :refer [current-user-express-middleware
                                                           user-context-fn
                                                           add-auth-error-plugin
                                                           build-user-timestamp-middleware]]
            [streamtide.server.syncer :as streamtide.server.syncer]
            [streamtide.shared.graphql-schema :refer [graphql-schema]]
            [streamtide.shared.smart-contracts-dev :as smart-contracts-dev]
            [streamtide.shared.smart-contracts-prod :as smart-contracts-prod]
            [streamtide.shared.smart-contracts-qa :as smart-contracts-qa]
            [taoensso.timbre :as log :refer [info warn error]])
  (:require-macros [streamtide.shared.utils :refer [get-environment]]))

(def body-parser (nodejs/require "body-parser"))

(defonce resync-count (atom 0))

(def contracts-var
  (condp = (get-environment)
    "prod" #'smart-contracts-prod/smart-contracts
    "qa" #'smart-contracts-qa/smart-contracts
    "dev" #'smart-contracts-dev/smart-contracts))

(defn start []
  (-> (mount/only #{#'district.server.config/config
                    #'district.server.db/db
                    #'district.server.graphql/graphql
                    #'district.server.logging/logging
                    #'district.server.smart-contracts/smart-contracts
                    #'district.server.web3-events/web3-events
                    #'district.server.web3/web3
                    #'streamtide.server.db/streamtide-db
                    #'streamtide.server.syncer/syncer})
      (mount/with-args
        {:config {:default {:logging {:level "info"
                                      :console? false}
                            :time-source :js-date
                            :graphql {:port 6300
                                      :middlewares [logging-middlewares
                                                    current-user-express-middleware
                                                    (build-user-timestamp-middleware 60000)
                                                    ;; TODO As we are sending the pictures directly in graphql, we need to increase the limit
                                                    (.json body-parser #js {:limit "5mb"})
                                                    ]
                                      :plugins [add-auth-error-plugin]
                                      :schema (graph-utils/build-schema graphql-schema
                                                                        resolvers-map
                                                                        {:kw->gql-name graphql-utils/kw->gql-name
                                                                         :gql-name->kw graphql-utils/gql-name->kw})
                                      :field-resolver (graph-utils/build-default-field-resolver graphql-utils/gql-name->kw)
                                      :path           "/graphql"
                                      :formatError (fn [formatted-error _raw-error]
                                                     (if (= "prod" (get-environment))
                                                       (clj->js (merge {:location (.-location formatted-error)
                                                                        :path (.-path formatted-error)}
                                                                       (if (= (-> formatted-error .-extensions .-code)
                                                                              "INTERNAL_SERVER_ERROR")
                                                                         {:message "Internal Server Error"}
                                                                         {:message (.-message formatted-error)})))
                                                       formatted-error))
                                      :context-fn     user-context-fn
                                      :graphiql       false}
                            :db {:transform-result-keys-fn (comp keyword demunge #(str/replace % #"_slash_" "_SLASH_"))}
                            :avatar-images {:fs-path "resources/public/img/avatar/"
                                            :url-path "/img/avatar/"}
                            :verifiers {:twitter {:consumer-key "PLACEHOLDER"
                                                  :consumer-secret "PLACEHOLDER"}}
                            :web3 {:url "ws://127.0.0.1:8546"
                                   :on-offline (fn []
                                                 (log/warn "Ethereum node went offline, stopping syncing modules" {:resyncs @resync-count} ::web3-watcher)
                                                 (mount/stop #'district.server.web3-events/web3-events
                                                             #'streamtide.server.syncer/syncer))
                                   :on-online (fn []
                                                (log/warn "Ethereum node went online again, starting syncing modules" {:resyncs (swap! resync-count inc)} ::web3-watcher)
                                                (mount/start #'district.server.web3-events/web3-events
                                                             #'streamtide.server.syncer/syncer))}
                            :syncer {:reload-interval 7200000}
                            :smart-contracts {:contracts-var contracts-var}
                            :web3-events {:events constants/web3-events
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