(ns streamtide.server.core
  "Main entry point of the server. Reads the config and starts all modules with mount"
  (:require [cljs.nodejs :as nodejs]
            [district.graphql-utils :as graphql-utils]
            [district.server.config :as district.server.config]
            [district.server.db :as district.server.db]
            [district.server.graphql :as district.server.graphql]
            [district.server.graphql.utils :as graph-utils]
            [district.server.logging :as district.server.logging]
            [district.server.middleware.logging :refer [logging-middlewares]]
            [district.shared.async-helpers :as async-helpers]
            [mount.core :as mount]
            [streamtide.server.constants :as constants]
            [streamtide.server.db :as streamtide.server.db]
            [streamtide.server.graphql.graphql-resolvers :refer [resolvers-map]]
            [streamtide.server.graphql.middlewares :refer [current-user-express-middleware user-context-fn]]
            [streamtide.server.syncer :as streamtide.server.syncer]
            [streamtide.shared.graphql-schema :refer [graphql-schema]]
            [streamtide.shared.smart-contracts-dev :as smart-contracts-dev]
            [streamtide.shared.smart-contracts-prod :as smart-contracts-prod]
            [streamtide.shared.smart-contracts-qa :as smart-contracts-qa]
            [taoensso.timbre :as log :refer [info warn error]])
  (:require-macros [streamtide.shared.utils :refer [get-environment]]))

(def body-parser (nodejs/require "body-parser"))


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
                    #'streamtide.server.db/streamtide-db
                    #'streamtide.server.syncer/syncer})
      (mount/with-args
        {:config {:default {:logging {:level "info"
                                      :console? false}
                            :time-source :js-date
                            :graphql {:port 6300
                                      :middlewares [logging-middlewares
                                                    current-user-express-middleware
                                                    ;; TODO As we are sending the pictures directly in graphql, we need to increase the limit
                                                    (.json body-parser #js {:limit "50mb"})
                                                    ]
                                      :schema (graph-utils/build-schema graphql-schema
                                                                        resolvers-map
                                                                        {:kw->gql-name graphql-utils/kw->gql-name
                                                                         :gql-name->kw graphql-utils/gql-name->kw})
                                      :field-resolver (graph-utils/build-default-field-resolver graphql-utils/gql-name->kw)
                                      :path           "/graphql"
                                      :context-fn     user-context-fn
                                      :graphiql       false}
                            :avatar-images {:fs-path "resources/public/img/avatar/"
                                            :url-path "/img/avatar/"}
                            ;:web3 {}
                            :syncer {}
                            ;:smart-contracts {:contracts-var contracts-var}
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