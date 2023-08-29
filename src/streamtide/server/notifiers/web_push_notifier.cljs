(ns streamtide.server.notifiers.web-push-notifier
  (:require
    [cljs.nodejs :as nodejs]
    [district.server.config :refer [config]]
    [district.shared.async-helpers :refer [<? safe-go]]
    [streamtide.server.db :as stdb]
    [streamtide.shared.utils :as shared-utils]))

(defonce web-push (nodejs/require "web-push"))

(def notifier-type (name :web-push))

(defn vapid-details []
  (let [web-push-config (-> @config :notifiers :web-push)]
    #js {:publicKey (:public-key web-push-config)
         :privateKey (:private-key web-push-config)
         :subject (:subject web-push-config)}))

(defn default-options []
  #js {:TTL 10000 :vapidDetails (vapid-details)})

(defn add-push-subscription [user-address subscription]
  (stdb/add-notification-type-many! {:user/address user-address
                                     :notification/user-id (shared-utils/json-stringify subscription)
                                     :notification/type notifier-type}))

(defn get-subscriptions [user-entries]
  (map (fn [{:keys [:notification/user-id :notification/id]}]
         {:subscription (shared-utils/json-parse user-id)
          :id id})
       user-entries))

(defn notify [user-entries {:keys [:title :body] :as message}]
  (let [notification (clj->js {:title title :options {:body body}})
        subscriptions (get-subscriptions user-entries)]
    (safe-go
      (loop [subscription subscriptions]
        (when subscription
          (let [s (first subscription)]
            (<?
              (-> (.sendNotification
                     web-push
                     (clj->js (:subscription s))
                     (shared-utils/json-stringify notification)
                     (default-options))
                  (.catch (fn [e]
                            (when (= 410 (.-statusCode e))
                              (stdb/remove-notification-type-many! {:user/address user-address
                                                                    :notification/id (:id s)
                                                                    :notification/type notifier-type}))))))
          (recur (next subscription))))))))
