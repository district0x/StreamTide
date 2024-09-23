(ns streamtide.ui.admin.campaigns.events
  (:require
    [district.ui.logging.events :as logging]
    [district.ui.notification.events :as notification-events]
    [district.ui.graphql.events :as gql-events]
    [re-frame.core :as re-frame]
    [streamtide.ui.components.error-notification :as error-notification]))

(re-frame/reg-event-fx
  ::create-campaign
  (fn [{:keys [db]} [_ {:keys [:user/address] :as data}]]
    (let [query
          {:queries [[:add-campaign
                      {:user/address :$address}]]
           :variables [{:variable/name :$address
                        :variable/type :ID!}]}]
      {:db (assoc-in db [:creating-campaign?] true)
       :dispatch [::gql-events/mutation
                  {:query query
                   :variables {:address address}
                   :on-success [::create-campaign-success data]
                   :on-error [::create-campaign-error {:user/address address}]}]})))


(re-frame/reg-event-fx
  ::create-campaign-success
  (fn [{:keys [db]} [_ args]]
    (let [{:keys [:user/address :on-success]} args]
      (when on-success
        (on-success))
      {:db (dissoc db :creating-campaign?)
       :dispatch [::notification-events/show "Campaign successfully created"]})))

(re-frame/reg-event-fx
  ::create-campaign-error
  (fn [{:keys [db]} [_ {:keys [:user/address]} error]]
    {:db (dissoc db :creating-campaign?)
     :dispatch-n [[::error-notification/show-error "An error occurs while creating campaign" error]
                  [::logging/error
                   (str "Failed to create campaign for user " address)
                   {:error (map :message error)} ::create-campaign]]}))
