(ns streamtide.ui.components.error-notification
  (:require
    [clojure.string :as str]
    [district.ui.notification.events :as notification-events]
    [reagent.core :as r]
    [re-frame.core :as re-frame]))


(defn error-notification [{:keys [:message :details]}]
  (let [details-visible (r/atom false)
        details? (not (str/blank? details))]
    (fn []
      [:div.error (merge {:on-click #(reset! details-visible true)}
                       (when (and details? (not @details-visible)) {:class "hidden-details"}))
       (str "[ERROR] " message ".")
       (when (and details? @details-visible) [:span.details (str "Details: " details)])])))

(defn- unwrap-error [js-error]
  (loop [js-error js-error]
    (if (and (coll? js-error) (not (map? js-error)))
      (recur (first js-error))
      js-error)))

(defn- parse-error [js-error]
  (let [js-error (unwrap-error js-error)]
    (let [{:keys [:message :error :code :status]} (js->clj js-error :keywordize-keys true)
          message (or message (.-message js-error))]
      (if (or (and code (< code 0) message) (= status :tx.status/error))
        "Transaction reverted"
        (or message error)))))

(re-frame/reg-event-fx
  ::show-error
  (fn [{:keys [db]} [_ message error]]
    {:dispatch-n [[::notification-events/hide-notification]
                  [::notification-events/clear-queue]]
     :dispatch-later {:ms 200
                      :dispatch
                      [::notification-events/show
                       {:message [error-notification {:message message
                                                      :details (parse-error error)}]}]}}))
