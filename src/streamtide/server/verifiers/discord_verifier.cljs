(ns streamtide.server.verifiers.discord-verifier
  (:require
    [cljs.nodejs :as nodejs]
    [district.server.config :refer [config]]
    [district.shared.async-helpers :refer [<? safe-go]]))

(defonce axios (nodejs/require "axios"))

(defn- get-token-request [code]
  (safe-go
    (let [discord-config (-> @config :verifiers :discord)
          {:keys [client-id client-secret callback]} discord-config
          token-request (<? (.post axios "https://discord.com/api/oauth2/token"
                               (str
                                 "grant_type=authorization_code"
                                 "&code=" code
                                 "&client_id=" client-id
                                 "&client_secret=" client-secret
                                 "&redirect_uri=" callback)))]
      (if (= (.-status token-request) 200)
        token-request
        (throw (js/Error. "Unexpected token-request status"))))))

(defn verify-oauth-verifier [{:keys [:code :state]}]
  (safe-go
    (let [token-request (<? (get-token-request code))
          access-token (-> token-request .-data .-access_token)
          user-request (<? (.get axios "https://discord.com/api/oauth2/@me"
                                 (clj->js {:headers {:Authorization (str "Bearer " access-token)}})))]
      (when (not= (.-status user-request) 200)
        (throw (js/Error. "Unexpected user-request status")))
      (let [user-id (-> user-request .-data .-user .-id)
            ;username (-> user-request .-data .-user .-username)
            ]
        (if user-id
          {:valid? true
           :url (str "https://discordapp.com/users/" user-id)}
          {:valid? false})))))
