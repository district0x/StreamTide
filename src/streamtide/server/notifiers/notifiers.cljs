(ns streamtide.server.notifiers.notifiers
  (:require
    [clojure.string :as string]
    [goog.string :as gstring]
    [streamtide.server.db :as stdb]
    [streamtide.shared.utils :as shared-utils]))


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

(defn- notify-all-types [notification-setting-entries notification]
  (let [notification-by-type (->> notification-setting-entries
                                  (map #(update % :notification/type keyword))
                                  (group-by :notification/type))]
    (doseq [[type users] notification-by-type]
      (let [ids (get-ids type (map :user/address users))]
        (notify type ids notification)))))

(defn notify-announcement [announcement]
  "Sends a notification to all subscribers when there is a new announcement"
  (let [notification {:category :announcements
                      :title "New Announcement"
                      :body (:announcement/text announcement)}
        notification-entries (stdb/get-notification-categories {:notification/category (name :notification-category/announcements)
                                                                :notification/enable true})]
    (notify-all-types notification-entries notification)))

(defn notify-new-content [{:keys [:user/address :user/name] :as creator}]
  "Sends a notification to the supported of a creator when there is new content"
  (let [notification {:title "New content from supported patron"
                      :body (gstring/format "The creator you support \"%s\" has added new content" name)}
        addresses (map :user/source-user (stdb/get-user-content-permissions {:user/target-user address}))
        enabled-notifications (stdb/get-notification-categories {:user/addresses addresses
                                                                 :notification/category (name :notification-category/patron-publications)
                                                                 :notification/enable true})]
    (notify-all-types enabled-notifications notification)))

(defn notify-donation [donation]
  "Sends a notification to the receiver of a donation"
  (let [sender (stdb/get-user (:donation/sender donation))
        notification {:title "Donation received"
                      :body (gstring/format "You have received a donation from %s of a value of %s"
                                            (if (string/blank? (:user/name sender)) (:donation/sender donation) (:user/name sender))
                                            (shared-utils/format-price (:donation/amount)))}
        notification-entries (stdb/get-notification-categories {:user/address (:donation/receiver donation)
                                                                :notification/category (name :notification-category/donations)
                                                                :notification/enable true})]
  (notify-all-types notification-entries notification)))

(defn notify-grants-statuses [grants-status]
  "Sends a notification to all the users whose grant status have changed"
  (let [approved? (= (:grant/status grants-status) "approved")
        notification {:title (if approved? "Grant approved!" "Grant rejected")
                      :body (if approved? "Congratulations! Your grant has been approved. You can now add content and enjoy the benefits of being a creator"
                                          "Unfortunately, your grant has been rejected. Contact our support team in Discord if you think there is some sort of mistake")}
        notification-entries (stdb/get-notification-categories {:user/addresses (:user/addresses grants-status)
                                                                :notification/category (name :notification-category/grant-status)
                                                                :notification/enable true})]
  (notify-all-types notification-entries notification)))
