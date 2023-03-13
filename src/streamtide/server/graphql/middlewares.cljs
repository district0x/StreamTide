(ns streamtide.server.graphql.middlewares
  "Defines some utils for extending graphql functionality, such as authentication"
  (:require [cljs.reader :refer [read-string]]
            [clojure.string :as string]
            [district.server.config :as config]
            [streamtide.server.graphql.authorization :as authorization]
            [streamtide.shared.utils :as shared-utils]))

(defn- bearer-token [auth-header]
  (second (string/split auth-header "Bearer ")))

(defn current-user-express-middleware [req _ next]
  "Middleware to include in the request header the user logged-in in the server"
  (let [secret (-> @config/config :graphql :sign-in-secret)
        headers (js->clj (.-headers req) :keywordize-keys true)
        auth-header (:authorization headers)
        current-user (authorization/token->user (bearer-token auth-header) secret)]
    (when current-user
      (aset (.-headers req) "current-user" (pr-str current-user))))
  (next))

(defn user-context-fn [event]
  "Adds the logged-in user, if any, server config and current time to the context"
  (let [user (read-string (aget event "req" "headers" "current-user"))
        timestamp (or (read-string (aget event "req" "headers" "timestamp"))
                      (shared-utils/now))]
    {:config @config/config
     :current-user user
     :timestamp timestamp}))
