(ns streamtide.ui.config
  "UI Config"
  (:require [mount.core :refer [defstate]]
            [streamtide.shared.graphql-schema :refer [graphql-schema]])
  (:require-macros [streamtide.shared.utils :refer [get-environment]]))


(def development-config
  {:debug? true
   :logging {:level :debug
             :console? true}
   :graphql {:schema graphql-schema
             :url "http://localhost:6300/graphql"
             :disable-default-middlewares? true
             }
   :router {:html5? true}
   :verifiers {:discord {:client-id "1035157814194221108"}}
   ;:domain "localhost"
   })

(def qa-config
  {:logging {:level :warn
             :console? true}
   :graphql {:schema graphql-schema
             :url "https://api.streamtide.qa.district0x.io/graphql"}
   :domain "TBD"})

(def production-config
  {:logging {:level :warn
             :console? false}
   :graphql {:schema graphql-schema
             :url "https://api.streamtide.io/graphql"}
   :domain "TBD"})

(def config-map
  (condp = (get-environment)
    "prod" production-config
    "qa" qa-config
    "dev" development-config))

(defn start []
  )

(defstate config
          :start (start)
          :stop ::stopped)
