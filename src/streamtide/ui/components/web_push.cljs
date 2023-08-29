(ns streamtide.ui.components.web-push
  "Handlers for subscribing to web push notifications"
  (:require [district.ui.logging.events :as logging]
            [district.ui.notification.events :as notification-events]
            [re-frame.core :refer [subscribe dispatch] :as re-frame]
            [district.ui.graphql.events :as gql-events]
            [streamtide.shared.utils :as shared-utils]
            [streamtide.ui.config :as config]))

; This ns provides the component, event handlers and effects to subscribe to push notifications.
; In short, the process is as follows:
; 1. The subscription starts by registering a service worker, which will be in charge of reacting on push events,
; into the browser (see streamtide.ui.service-worker).
; 2. Once registered, it will prompt the user to allow notifications from current website. (skipped if already allowed)
; 3. If allowed, it subscribes the service worker to the push notification service - and endpoint, given by the browser
; vendor, in charge of sending the received notifications to the user. (skipped if already subscribed)
; 4. The subscription details are finally sent to the server, so it knows how to 'talk' to the push notification
; service, so the notifications reach the user's browser.
;
; Note: subscription process needs to be triggered from a button click event or similar as most browser block permission
; request if not started from an event explicitly triggered by the user (e.g., cannot be triggered on window open)
; Note 2: push notifications are not a 100% reliable notification mechanism. Even if the subscription succeeded,
; the notifications might be blocked by ad-blockers or even the OS itself.


(defn subscribe-button []
  "Entry point component. Add this somewhere to trigger the push notification subscription process"
  [:button
   {:on-click
    #(dispatch [::enable-push-notifications])}
   "CLICK"])

(re-frame/reg-fx
  :register-service-worker
  (fn [{:keys [:on-success :on-error]}]
    (-> js/navigator .-serviceWorker (.register "/js/service-worker.js")
        (.then
          (fn [registration]
            (dispatch [::logging/info "Service worker for web push notifications registered"])
            (when on-success (dispatch (conj on-success {:registration registration})))))
        (.catch (fn [error]
                  (when on-error (dispatch (conj on-error {:error error}))))))))

(re-frame/reg-event-fx
  ::enable-push-notifications
  ; request subscribing to push notifications
  (fn [{:keys [db]} [_]]
    (when (and (aget js/navigator "serviceWorker") (aget js/window "PushManager")) ; check browser supports notifications
      {:register-service-worker {:on-success [::request-permissions]
                                 :on-error [::log-and-show "Failed to register service worker for web push notifications"]}})))

(re-frame/reg-fx
  :request-notification-permission
  (fn [{:keys [:on-granted :on-denied :on-error]}]
    (-> (.requestPermission js/Notification)
        (.then (fn [permission]
                 (if (= permission "granted")
                   (when on-granted (dispatch on-granted))
                   (when on-denied (dispatch on-denied)))))
        (.catch (fn [error]
                  (when on-error (dispatch (conj on-error {:error error})))))))  )

(re-frame/reg-event-fx
  ::request-permissions
  ; After registering the service worker, we (the browser, actually) prompt the user whether she allows push notifications
  (fn [{:keys [db]} [_ {:keys [:registration] :as data}]]
    {:request-notification-permission
     {:on-granted [::permission-granted data]
      :on-denied [::permission-denied data]
      :on-error [::log-and-show "Failed to request push notifications permissions"]}}))

(re-frame/reg-fx
  :get-subscription
  (fn [{:keys [:registration :when-subscribed :when-not-subscribed :on-error]}]
    (-> registration .-pushManager .getSubscription
        (.then (fn [subscription]
                 (if subscription
                   (when when-subscribed (dispatch (conj when-subscribed {:subscription subscription})))
                   (when when-not-subscribed (dispatch when-not-subscribed)))))
        (.catch (fn [error]
                  (when on-error (dispatch (conj on-error {:error error}))))))))

(re-frame/reg-event-fx
  ::permission-granted
  ; When push notifications are granted, we need to subscribe to the notifications
  (fn [{:keys [db]} [_ {:keys [:registration] :as data}]]
    {:get-subscription {:registration registration
                        :when-subscribed [::add-subscription]
                        :when-not-subscribed [::subscribe data]
                        :on-error [::log-and-show "Failed to subscribe to push notifications"]}}))

(re-frame/reg-event-fx
  ::permission-denied
  ; If users do not allow push notifications, we just show an error
  (fn [{:keys [db]} [_ {:keys [:registration] :as data}]]
    {:dispatch-n [[::logging/error "Permission for sending push notifications denied"]
                  [::notification-events/show "[ERROR] Permission for sending push notifications denied"]]}))

(re-frame/reg-fx
  :push-subscribe
  (fn [{:keys [:registration :on-subscription :on-error]}]
    (-> registration .-pushManager
        (.subscribe #js {:userVisibleOnly true
                         :applicationServerKey (-> config/config-map :notifiers :web-push :public-key)})
        (.then (fn [subscription]
                 (when on-subscription (dispatch (conj on-subscription {:subscription subscription})))))
        (.catch (fn [error]
                  (when on-error (dispatch (conj on-error {:error error}))))))))

(re-frame/reg-event-fx
  ::subscribe
  ; subscribe to the notifications
  (fn [{:keys [db]} [_ {:keys [:registration] :as data}]]
    {:push-subscribe {:registration registration
                      :on-subscription [::add-subscription]
                      :on-error [::log-and-show "Failed to subscribe to push notification"]}}))

(re-frame/reg-event-fx
  ::add-subscription
  ; Sends a GraphQL mutation request to add a new web-push subscription in the server
  (fn [{:keys [db]} [_ {:keys [:subscription] :as data}]]
    (let [query
          {:queries [[:add-push-subscription
                      {:subscription :$subscription}]]
           :variables [{:variable/name :$subscription
                        :variable/type :String!}]}]
      {:dispatch [::gql-events/mutation
                  {:query query
                   :variables {:subscription (shared-utils/json-stringify subscription)}
                   :on-success [::add-subscription-success data]
                   :on-error [::add-subscription-error data]}]})))

(re-frame/reg-event-fx
  ::add-subscription-success
  (fn [{:keys [db]} [_ data result]]
    (merge
      {:dispatch [::notification-events/show "Subscribed to Push Notification"]})))

(re-frame/reg-event-fx
  ::add-subscription-error
  (fn [{:keys [db]} [_ {:keys [:subscription] :as data} error]]
    {:dispatch-n [[::logging/error
                   "Failed to subscribe to push notifications"
                   {:error (map :message error)
                    :subscription subscription} ::add-subscription]
                  [::notification-events/show "[ERROR] An error occurs while subscribing to push notifications"]]}))

(re-frame/reg-event-fx
  ::log-and-show
  (fn [{:keys [db]} [_ message details]]
    (merge
      {:dispatch-n [[::logging/error message details]
                    [::notification-events/show "[ERROR] Cannot subscribe to push notifications"]]})))
