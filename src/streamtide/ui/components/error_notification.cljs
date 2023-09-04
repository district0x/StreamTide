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

(defn- parse-error [js-error]
  (let [js-error (if (and (coll? js-error) (not (map? js-error))) (first js-error) js-error)]
  (-> (js->clj js-error :keywordize-keys true)
      :message)))

(re-frame/reg-event-fx
  ::show-error
  (fn [{:keys [db]} [_ message [error]]]
    {:dispatch [::notification-events/show
                {:message [error-notification {:message message
                                               :details (parse-error error)}]}]}))
