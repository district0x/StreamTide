(ns streamtide.ui.service-worker
  "Service Worker for listening to push messages and show notifications")

(def image "/img/layout/logo-icon.svg")


(defn handle-push [event]
  "Show a notification when receiving a push event"
  (let [data (-> event .-data .json (js->clj :keywordize-keys true))
        options {:body (-> data :options :body)
                 :icon image}]
    (-> js/self .-registration (.showNotification (:title data) (clj->js options)))))

(defn handle-notificationclick [event]
  "When clicking on a notification, focus the streamtide window/tab or open a new one if not already opened"
  (-> event .-notification .close)
  (let [url (-> js/self .-location .-origin)]
    (-> js/clients (.matchAll #js {:type "window" :includeUncontrolled true})
        (.then (fn [window-clients]
                 (let [client (first (filter (fn [client]
                                        (and (-> client .-url (.startsWith url))
                                             (aget client "focus"))) window-clients))]
                   (if client
                     (.focus client)
                     (when (aget js/clients "openWindow")
                       (.openWindow js/clients url)))))))))


(.addEventListener js/self "push" #(.waitUntil % (handle-push %)))
(.addEventListener js/self "notificationclick" #(.waitUntil % (handle-notificationclick %)))
