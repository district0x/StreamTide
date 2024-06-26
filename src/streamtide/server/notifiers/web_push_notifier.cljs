(ns streamtide.server.notifiers.web-push-notifier
  (:require
    [cljs.nodejs :as nodejs]
    [clojure.string :as string]
    [district.server.config :refer [config]]
    [district.shared.async-helpers :refer [<? safe-go]]
    [streamtide.server.db :as stdb]
    [streamtide.shared.utils :as shared-utils]
    [streamtide.server.notifiers.notifiers :refer [notify store-id get-ids]]
    [taoensso.timbre :as log]))

(def notifier-type-kw :web-push)
(def notifier-type (name notifier-type-kw))
(defonce web-push (nodejs/require "web-push"))

(def valid-web-push-endpoints-domains #{"android.googleapis.com"
                                        "fcm.googleapis.com"
                                        "updates.push.services.mozilla.com"
                                        "updates-autopush.stage.mozaws.net"
                                        "updates-autopush.dev.mozaws.net"
                                        "notify.windows.com"
                                        "push.apple.com"})

(defn vapid-details []
  (let [web-push-config (-> @config :notifiers :web-push)]
    #js {:publicKey (:public-key web-push-config)
         :privateKey (:private-key web-push-config)
         :subject (:subject web-push-config)}))

(defn default-options []
  #js {:TTL 86400 ; 24 hours
       :vapidDetails (vapid-details)})

(defn valid-subscription? [subscription]
  (let [subscription (shared-utils/json-parse subscription)
        endpoint-domain (shared-utils/url->domain (.-endpoint subscription))
        keys (.-keys subscription)]
    (and (some? endpoint-domain)
      (some #(string/ends-with? endpoint-domain %) valid-web-push-endpoints-domains)
         (some? keys)
         (some? (.-auth keys))
         (some? (.-p256dh keys)))))

(defn add-push-subscription [user-address subscription-id]
  (when-not (valid-subscription? subscription-id)
    (throw (str "Invalid Subscription: " subscription-id)))
  (stdb/add-notification-type-many! {:user/address user-address
                                     :notification/user-id subscription-id
                                     :notification/type notifier-type}))

(defn get-subscriptions [addresses]
  (stdb/get-notification-types-many {:user/addresses addresses
                                     :notification/type notifier-type}))

(defn remove-subscription [subscription]
  (stdb/remove-notification-type-many! (select-keys subscription [:notification/id :user/address :notification/type])))

(defn web-push-notify [subscriptions {:keys [:title :body] :as message}]
  (let [notification (clj->js {:title title :options {:body body}})]
    (safe-go
      (loop [subscriptions subscriptions]
        (when subscriptions
          (let [notification-entry (first subscriptions)]
            (log/debug "Sending notification" (merge notification-entry message))
            (<?
              (-> (.sendNotification
                     web-push
                     (-> notification-entry :notification/user-id shared-utils/json-parse clj->js)
                     (shared-utils/json-stringify notification)
                     (default-options))
                  (.catch (fn [e]
                            (when (= 410 (.-statusCode e))
                              (log/info "Web push endpoint-domain is no longer active. Removing it" notification-entry)
                              (<? (remove-subscription notification-entry)))))))
          (recur (next subscriptions))))))))

(defmethod notify notifier-type-kw [_ users notification]
  (web-push-notify users notification))

(defmethod store-id notifier-type-kw [_ address id]
  (add-push-subscription address id))

(defmethod get-ids notifier-type-kw [_ addresses]
  (get-subscriptions addresses))
