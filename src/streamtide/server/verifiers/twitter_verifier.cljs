(ns streamtide.server.verifiers.twitter-verifier
  (:require
    ["twitter-api-sdk" :as twitter-sdk]
    [district.server.config :refer [config]]
    [district.shared.async-helpers :refer [<? safe-go]]
    [streamtide.server.verifiers.verifiers :as verifiers]
    [streamtide.shared.utils :as shared-utils]))

(defonce Client (.-Client twitter-sdk))
(defonce auth (.-auth twitter-sdk))

(defonce clients (atom {}))

(defonce CLIENT-TIMEOUT 300000)  ; 5 mins

(defn- init-auth-client [callback]
  (let [; TODO change way to get config, maybe this module should not know the full path of where the config is
        twitter-config (-> @config :verifiers :twitter)]

    (auth.OAuth2User. (clj->js (merge
                                 {:client_id (:consumer-key twitter-config)
                                  :client_secret (:consumer-secret twitter-config)
                                  :scopes ["tweet.read" "users.read"]}
                                 (when callback {:callback callback}))))))

(defn delete-client [client-uuid]
  (swap! clients dissoc client-uuid))

(defn verify-oauth-verifier [{:keys [:code :state]}]
  (safe-go
    (if-let [auth-client (get @clients state)]
      (do
        (<? (.requestAccessToken auth-client code))
        (delete-client state) ; if auth code is valid, we no longer need the client
        (let [tw-client (Client. auth-client)
              user (<? (.findMyUser (.-users tw-client)))]
          (if user
            {:valid? true
             :url (str "https://x.com/" (-> user (js->clj :keywordize-keys true) :data :username))}
            {:valid? false
             :message "Cannot found user"})))
      (throw (js/Error. "Auth client invalid or expired")))))

(defn generate-twitter-oauth-url [{:keys [:callback] :as args}]
  (let [auth-client (init-auth-client callback)
        client-uuid (shared-utils/network->uuid :twitter)]
    (swap! clients assoc client-uuid auth-client)
    (js/setTimeout #(delete-client client-uuid) CLIENT-TIMEOUT)
    (.generateAuthURL auth-client #js {:state client-uuid
                                       :code_challenge_method "s256"})))

(defmethod verifiers/verify :twitter [_ args]
  (verify-oauth-verifier args))
