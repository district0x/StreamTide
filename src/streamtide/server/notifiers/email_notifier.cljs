(ns streamtide.server.notifiers.email-notifier
  (:require
    [clojure.string :as string]
    [district.server.config :refer [config]]
    [district.sendgrid :as sendgrid]
    [district.shared.async-helpers :refer [<? safe-go]]
    [streamtide.server.db :as stdb]
    [streamtide.server.notifiers.notifiers :refer [notify store-id get-ids]]
    [streamtide.shared.utils :refer [valid-email?]]
    [taoensso.timbre :as log]))


(def notifier-type-kw :email)
(def notifier-type (name notifier-type-kw))

(defn set-email [user-address email]
  (safe-go
    (if (string/blank? email)
      (<? (stdb/remove-notification-type! {:user/address user-address
                                           :notification/type notifier-type}))
      (do
        (when-not (valid-email? email)
          (throw (str "Invalid Email: " email)))
        (<? (stdb/upsert-notification-type! {:user/address user-address
                                             :notification/user-id email
                                             :notification/type notifier-type}))))))

(defn get-emails [addresses]
  (stdb/get-notification-types {:user/addresses addresses
                                :notification/type notifier-type}))

(defn send-email [email message]
  (safe-go
    (let [{:keys [:from :template-id :api-key :print-mode?]} (-> @config :notifiers :email)]
      (sendgrid/send-email
        (cond->
          {:from from
           :to email
           :subject (:title message)
           :content (str (:body message))
           :api-key api-key
           :print-mode? print-mode?
           :on-error #(log/error "Error sending email" {:error % :to email})
           :on-success #(log/debug "Email sent" {:message message :to email})}
          template-id (merge {:template-id template-id
                              :dynamic-template-data {:title (:title message)
                                                      :body (:body message)}}))))))

(defn email-notify [user-entries {:keys [:title :body] :as message}]
  (safe-go
    (loop [user-entries user-entries]
      (when user-entries
        (let [user-entry (first user-entries)]
          (log/debug "Sending email" (merge user-entry message))
          (send-email (:notification/user-id user-entry) message)
          (recur (next user-entries)))))))

(defmethod notify notifier-type-kw [_ users notification]
  (email-notify users notification))

(defmethod store-id notifier-type-kw [_ address id]
  (set-email address id))

(defmethod get-ids notifier-type-kw [_ addresses]
  (get-emails addresses))
