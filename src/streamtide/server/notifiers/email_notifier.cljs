(ns streamtide.server.notifiers.email-notifier
  (:require
    [district.server.config :refer [config]]
    [district.shared.async-helpers :refer [<? safe-go]]
    [streamtide.server.db :as stdb]
    [streamtide.server.notifiers.notifiers :refer [notify store-id get-ids]]
    [streamtide.shared.utils :refer [valid-email?]]
    [taoensso.timbre :as log]))


(def notifier-type-kw :email)
(def notifier-type (name notifier-type-kw))

(defn set-email [user-address email]
  (when-not (valid-email? email)
    (throw (str "Invalid Email: " email)))
  (stdb/upsert-notification-type! {:user/address user-address
                                   :notification/user-id email
                                   :notification/type notifier-type}))

(defn get-emails [addresses]
  (stdb/get-notification-types {:user/addresses addresses
                                :notification/type notifier-type}))

(defn email-notify [user-entries {:keys [:title :body] :as message}]
  (safe-go
    (loop [user-entries user-entries]
      (when user-entries
        (let [user-entry (first user-entries)]
          ; TODO
          (log/debug "Sending email" (merge user-entry message))
          (recur (next user-entries)))))))

(defmethod notify notifier-type-kw [_ users notification]
  (email-notify users notification))

(defmethod store-id notifier-type-kw [_ address id]
  (set-email address id))

(defmethod get-ids notifier-type-kw [_ addresses]
  (get-emails addresses))
