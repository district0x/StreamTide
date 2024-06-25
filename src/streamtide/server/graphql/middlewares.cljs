(ns streamtide.server.graphql.middlewares
  "Defines some utils for extending graphql functionality, such as authentication"
  (:require [cljs.reader :refer [read-string]]
            [clojure.string :as string]
            [district.server.config :as config]
            [district.shared.async-helpers :refer [<? safe-go]]
            [streamtide.server.db :as stdb]
            [streamtide.server.graphql.authorization :as authorization]
            [streamtide.shared.utils :as shared-utils]
            [taoensso.timbre :as log]))

(defn- bearer-token [auth-header]
  (second (string/split auth-header "Bearer ")))

(defn current-user-express-middleware [req _ next]
  "Middleware to include in the request header the user logged-in in the server"
  (let [secret (-> @config/config :graphql :sign-in-secret)
        headers (js->clj (.-headers req) :keywordize-keys true)
        auth-header (:authorization headers)
        {:keys [:user/address :error]} (when auth-header
                                         (authorization/token->user (bearer-token auth-header) secret))]
    (when address
      (aset (.-headers req) "current-user" (pr-str {:user/address address})))
    (when error
      (aset (.-headers req) "auth-error" (pr-str true))))
  (next))

(defn build-user-timestamp-middleware [interval]
  "Builds middleware to store in a DB the last interaction timestamp of a user.
  This must go after [current-user-express-middleware].
  To optimize performance, it does not write in the DB everytime a user interaction comes, but it keeps
  the users who has recently interacted in a set in memory instead, and periodically (interval) it writes
  all of them in DB"
  (let [users-interacted (atom #{})]
    (js/setInterval
      (fn []
        (try
          (when (not-empty @users-interacted)
            (safe-go
              (let [[users _] (swap-vals! users-interacted (fn [] #{}))]
                (<? (stdb/ensure-users-exist! users))
                (<? (stdb/set-user-timestamp! {:user-addresses users
                                               :timestamp/last-seen (- (shared-utils/now-secs) (/ interval 1000))})))))
          (catch :default e
            (log/error "Failed to store user timestamps" {:error e}))))
      interval)
    (fn [req _rep next]
      (let [user (-> (aget req "headers" "current-user") read-string :user/address)]
        (when user
          (swap! users-interacted conj user)))
      (next))))

(defn user-context-fn [event]
  "Adds the logged-in user, if any, server config and current time to the context"
  (let [user (read-string (aget event "req" "headers" "current-user"))
        auth-error (read-string (aget event "req" "headers" "auth-error"))
        timestamp (or (read-string (aget event "req" "headers" "timestamp"))
                      (shared-utils/now))]
    {:config @config/config
     :current-user user
     :timestamp timestamp
     :auth-error auth-error}))

(def add-auth-error-plugin
  ;Plugin which would include an extension in the message if there was an error with the user authentication
  #js {:requestDidStart
       (fn []
         #js {:willSendResponse
              (fn [request-context]
                (when (-> request-context .-contextValue :auth-error)
                  (let [response (.-response request-context)]
                    (when (and (= (-> response .-body .-kind) "single")
                               (-> response .-body .-singleResult (.hasOwnProperty "data")))
                      (when-not (-> response .-body .-singleResult (.hasOwnProperty "extensions"))
                        (aset (-> response .-body .-singleResult) "extensions" #js {}))
                      (aset (-> response .-body .-singleResult .-extensions) "error" "invalid authentication")))))})})
