(ns streamtide.server.notifiers.discord-notifier
  (:require
    ["axios" :as axios]
    [cljs.core.async :refer [<!]]
    [clojure.string :as string]
    [district.server.config :refer [config]]
    [district.shared.async-helpers :refer [<? safe-go]]
    [streamtide.server.db :as stdb]
    [streamtide.server.notifiers.notifiers :refer [notify store-id get-ids]]
    [taoensso.timbre :as log]))


(def notifier-type-kw :discord)
(def notifier-type (name notifier-type-kw))

(defn- build-content [message]
  (str "** " (:title message) " **\n" (:body message)))

(defn- send-discord-message [user-id message]
  (safe-go
    (let [token (-> @config :notifiers :discord :token)
          headers (clj->js {:headers {:Authorization (str "Bot " token)}})
          create-dm (.post axios "https://discord.com/api/v10/users/@me/channels"
                           (clj->js {:recipient_id user-id})
                           headers)]
      (<! (-> create-dm
              (.then (fn [result]
                       (let [channel-id (-> result .-data .-id)]
                         (when channel-id
                           (let [message (.post axios (str "https://discord.com/api/v10/channels/" channel-id "/messages")
                                                (clj->js {:content (build-content message)})
                                                headers)]
                             (-> message
                                 (.then (fn []
                                          (log/debug "Discord message sent" {:message message :user-id user-id})))
                                 (.catch (fn [error]
                                           (log/error "Failed to send discord message" {:error error
                                                                                        :error-data (-> error .-response .-data)
                                                                                        :channel-id channel-id
                                                                                        :user-id user-id})))))))))
              (.catch (fn [error]
                        (log/error "Failed to create discord direct message channel" {:error error
                                                                                      :error-data (-> error .-response .-data)
                                                                                      :user-id user-id}))))))))

(defn discord-notify [user-entries {:keys [:title :body] :as message}]
  (safe-go
    (loop [user-entries user-entries]
      (when user-entries
        (let [user-entry (first user-entries)]
          (log/debug "Sending Discord notification" (merge user-entry message))
          (<? (send-discord-message (:notification/user-id user-entry) message))
          (recur (next user-entries)))))))

(defn get-discord-id [addresses]
  (safe-go
    (map #(assoc % :notification/user-id (last (string/split (:social/url %) "/")))
         (<? (stdb/get-user-socials {:user/addresses addresses
                                     :social/network (name :discord)})))))

(defmethod notify notifier-type-kw [_ users notification]
  (discord-notify users notification))

(defmethod store-id notifier-type-kw [_ _address _id]
  ; Nothing to store. It will get the id from the social networks table
  )

(defmethod get-ids notifier-type-kw [_ addresses]
  (get-discord-id addresses))
