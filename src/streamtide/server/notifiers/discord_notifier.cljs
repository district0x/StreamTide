(ns streamtide.server.notifiers.discord-notifier
  (:require
    [clojure.string :as string]
    [district.server.config :refer [config]]
    [district.shared.async-helpers :refer [<? safe-go]]
    [streamtide.server.db :as stdb]
    [streamtide.server.notifiers.notifiers :refer [notify store-id get-ids]]
    [taoensso.timbre :as log]))


(def notifier-type-kw :discord)
(def notifier-type (name notifier-type-kw))

(defn discord-notify [user-entries {:keys [:title :body] :as message}]
  (safe-go
    (loop [user-entries user-entries]
      (when user-entries
        (let [user-entry (first user-entries)]
          ; TODO
          (log/debug "Sending Discord notification" (merge user-entry message))
          (recur (next user-entries)))))))

(defn get-discord-id [addresses]
  (map #(update % :social/url (fn [url]
                                (last (string/split url "/"))))
       (stdb/get-user-socials {:user/addresses addresses
                               :social/network (name :discord)})))

(defmethod notify notifier-type-kw [_ users notification]
  (discord-notify users notification))

(defmethod store-id notifier-type-kw [_ _address _id]
  ; Nothing to store. It will get the id from the social networks table
  )

(defmethod get-ids notifier-type-kw [_ addresses]
  (get-discord-id addresses))
