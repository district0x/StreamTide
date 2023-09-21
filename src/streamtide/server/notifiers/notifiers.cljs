(ns streamtide.server.notifiers.notifiers
  (:require
    [goog.string :as gstring]
    [streamtide.server.db :as stdb]))


(defmulti notify
          "Notify a group of users through a specific mechanism"
          (fn [type _users _notification]
            type))

(defmulti store-id
          "Store an id for a specific user such that it can notify it later on for a given type"
          (fn [type _address _id]
            type))

(defmulti get-ids
          "Get stored ids for the given users such that they can be notified"
          (fn [type _addresses]
            type))

(defmethod notify :default [type _ _]
  (js/Error. (str "Notification type not supported: " type)))

(defmethod store-id :default [type _ _]
  (js/Error. (str "Notification type not supported: " type)))

(defmethod get-ids :default [type _]
  (js/Error. (str "Notification type not supported: " type)))

(defn notify-category [{:keys [:category] :as notification}]
  "Send a notification to all users subscribed to a given category"
  (let [notification-by-type
        (->> (stdb/get-notification-receivers (name category))
             (map #(update % :notification/type keyword))
             (group-by :notification/type))]
    (doseq [[type users] notification-by-type]
      (let [ids (get-ids type (map :user/address users))]
        (notify type ids notification)))))
