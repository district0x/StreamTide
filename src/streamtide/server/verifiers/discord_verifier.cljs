(ns streamtide.server.verifiers.discord-verifier
  (:require
    [cljs.nodejs :as nodejs]
    [cljs.core.async :refer [<!]]
    [district.server.config :refer [config]]
    [district.shared.async-helpers :refer [<? safe-go]]
    [streamtide.server.verifiers.verifiers :as verifiers]))

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

(defn- get-user-roles [user-id]
  (safe-go
    (let [discord-config (-> @config :verifiers :discord)
          {:keys [token guild-id]} discord-config
          roles-request (.get axios (str "https://discord.com/api/v10/guilds/"
                                         guild-id "/members/" user-id)
                              (clj->js {:headers {:Authorization (str "Bot " token)}}))]
      (<! (-> roles-request
              (.then (fn [result]
                       (let [user-roles (-> result .-data .-roles)]
                         (if user-roles user-roles []))))
              (.catch (fn [error]
                        (if (= 10013 (-> error .-response .-data .-code)) nil
                        (throw error)))))))))

(defn- check-roles [user-roles]
  (let [roles (-> @config :verifiers :discord :roles)]
    (not-empty (clojure.set/intersection (set roles) (set user-roles)))))

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
            user-roles (when user-id (<? (get-user-roles user-id)))]
        (cond
          (not user-id)
          {:valid? false
           :message "Cannot get user info. Is bot authorized?"}

          (nil? user-roles)
          {:valid? false
           :message "The user do not belong to district0x server"}

          (check-roles user-roles)
          {:valid? true
           :url (str "https://discordapp.com/users/" user-id)}

          :else
          {:valid? false
           :message "User do not belong to any of the expected roles"})))))

(defmethod verifiers/verify :discord [_ args]
  (verify-oauth-verifier args))
