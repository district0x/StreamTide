(ns district.server.async-db
  "PostgreSQL async database implementation using connection pool.
   Copied from Ethlance for compatibility with d0x infrastructure."
  (:refer-clojure :exclude [get run!])
  (:require
    ["pg" :as pg]
    [cljs.core.async :refer [<!]]
    [clojure.string :as str]
    [district.server.config :refer [config]]
    [district.server.logging]
    [district.shared.async-helpers :refer [safe-go <?]]
    [honeysql-postgres.format]
    [honeysql-postgres.helpers]
    [honeysql.core :as sql]
    [honeysql.format :as sql-format]
    [honeysql.helpers]
    [mount.core :as mount :refer [defstate]]
    [taoensso.timbre :as log]))


(def Pool (.-Pool ^js pg))

(declare db start stop)


(def mount-state-key
  "Key defining our mount component within the district configuration"
  :district/db)


(defstate ^{:on-reload :noop} db
  :start (start (merge (clojure.core/get @config mount-state-key)
                       (mount-state-key (mount/args))))
  :stop (stop))


(defn sql-name-transform-fn
  [n]
  (-> n
      munge
      (str/replace "_STAR_" "*")
      (str/replace "_PERCENT_" "%")))


(def transform-result-keys-fn
  (comp keyword
        demunge
        #(str/replace % #"_slash_" "_SLASH_")))


(defn- map-keys
  [f m]
  (into {} (map (fn [[k v]] [(f k) v]) m)))


(defn get-connection
  "Returns a db connection from the pool."
  []
  (.connect (:connection-pool @db)))


(defn release-connection
  "Returns a db connection to the pool."
  [conn]
  (.release conn))


(defn run!
  "Given a db connection and a honey sql query runs it and returns its result."
  [conn statement]
  (safe-go
    (let [[query-str & values] (binding [sql-format/*name-transform-fn* sql-name-transform-fn]
                                 (sql/format statement
                                             :parameterizer :postgresql
                                             :allow-namespaced-names? true))
          res (<? (.query conn query-str (clj->js (or values []))))]
      (->> (js->clj (.-rows res))
           (map #(map-keys transform-result-keys-fn %))))))


(defn run-raw!
  "Given a db connection and raw SQL string run & return result"
  ([query-str]
   (run-raw! (get-connection) query-str []))
  ([query-str values]
   (run-raw! (get-connection) query-str values))
  ([conn-or-chan query-str values]
   (safe-go
     (let [conn (if (= js/Promise (type conn-or-chan)) (<! conn-or-chan) conn-or-chan)
           res (<! (.query conn query-str (clj->js (or values []))))]
       (->> (js->clj (.-rows res))
            (map #(map-keys transform-result-keys-fn %)))))))


(defn all
  "Given a db connection and a honey sql query runs it and returns resultset rows."
  [conn q]
  (run! conn q))


(defn get
  "Given a db connection and a honey sql query runs it and returns first resultset row."
  [conn q]
  (safe-go
    (first (<? (all conn q)))))


;;
;; Transaction management ;;
;;

(defn begin-tx
  [conn]
  (.query conn "BEGIN"))


(defn commit-tx
  [conn]
  (.query conn "COMMIT"))


(defn rollback-tx
  [conn]
  (.query conn "ROLLBACK"))


;;
;; Mount component ;;
;;

(defn start
  "Start the db mount component."
  [{:keys [user host database password port] :as opts}]
  (log/info "Starting DB component" opts)
  (let [pool (Pool. #js {:user user
                         :host host
                         :database database
                         :password password
                         :port port})]
    (.on pool "error" (fn [err _]
                        (log/error "Unexpected error on idle client" {:err err})))

    (log/info "DB component started")
    {:connection-pool pool}))


(defn stop
  "Stop the db mount component."
  []
  (when-let [pool (:connection-pool @db)]
    (.end pool))
  ::stopped)


;;
;; Simplified API (compatible with district-server-db-async interface) ;;
;; These functions manage connections internally for simpler usage
;;

(defn run!-auto
  "Run a query with automatic connection management.
   Compatible with district-server-db-async API where connection is managed internally."
  ([statement] (run!-auto statement nil))
  ([statement {:keys [format-opts]}]
   (safe-go
     (let [conn (<? (get-connection))]
       (try
         (let [[query-str & values] (binding [sql-format/*name-transform-fn* sql-name-transform-fn]
                                      (sql/format statement
                                                  :parameterizer (or (:parameterizer format-opts) :postgresql)
                                                  :allow-namespaced-names? (if (contains? format-opts :allow-namespaced-names?)
                                                                             (:allow-namespaced-names? format-opts)
                                                                             true)))
               res (<? (.query conn query-str (clj->js (or values []))))]
           (->> (js->clj (.-rows res))
                (map #(map-keys transform-result-keys-fn %))))
         (finally
           (release-connection conn)))))))


(defn all-auto
  "Query all rows with automatic connection management.
   Compatible with district-server-db-async API."
  ([statement] (run!-auto statement nil))
  ([statement opts] (run!-auto statement opts)))


(defn get-auto
  "Query single row with automatic connection management.
   Compatible with district-server-db-async API."
  ([statement] (get-auto statement nil))
  ([statement opts]
   (safe-go
     (first (<? (all-auto statement opts))))))
