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
        {:keys [:user/address :error]} (when auth-header
                                         (authorization/token->user (bearer-token auth-header) secret))]
    (when address
      (aset (.-headers req) "current-user" (pr-str {:user/address address})))
    (when error
      (aset (.-headers req) "auth-error" (pr-str true))))
  (next))

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
