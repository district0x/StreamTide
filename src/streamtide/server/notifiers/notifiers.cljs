(ns streamtide.server.notifiers.notifiers
  (:require [streamtide.server.db :as stdb]
            [streamtide.server.notifiers.web-push-notifier :as web-push-notifier]))


(defmulti notify
          "Notify a group of users through a specific mechanism"
          (fn [type _users _notification]
            type))

(defn notify-category [{:keys [:category] :as notification}]
  "Send a notification to all users subscribed to a given category"
  (let [notification-by-type
        (->> (stdb/get-notification-receivers (name category))
             (map #(update % :notification/type keyword))
             (group-by :notification/type))]
    (doseq [[type users] notification-by-type]
      (notify type users notification))))

(defmethod notify :default [type _ _]
  (js/Error. (str "Notification type not supported: " type)))

(defmethod notify :web-push [_ users notification]
  (web-push-notifier/notify users notification))
