(ns streamtide.ui.admin.campaign.events
  (:require
    [district.ui.logging.events :as logging]
    [district.ui.notification.events :as notification-events]
    [district.ui.graphql.events :as gql-events]
    [re-frame.core :as re-frame]
    [streamtide.ui.components.error-notification :as error-notification]))

(re-frame/reg-event-fx
  ::remove-campaign
  (fn [{:keys [db]} [_ {:keys [:campaign/id] :as data}]]
    (let [query
          {:queries [[:remove-campaign
                      {:campaign/id :$campaign}]]
           :variables [{:variable/name :$campaign
                        :variable/type :ID!}]}]
      {:db (assoc-in db [:removing-campaign? id] true)
       :dispatch [::gql-events/mutation
                  {:query query
                   :variables {:campaign id}
                   :on-success [::remove-campaign-success data]
                   :on-error [::remove-campaign-error {:campaign/id id}]}]})))


(re-frame/reg-event-fx
  ::remove-campaign-success
  (fn [{:keys [db]} [_ args]]
    (let [{:keys [:campaign/id :on-success]} args]
      (when on-success
        (on-success))
      {:db (update db :removing-campaign? dissoc id)
       :dispatch [::notification-events/show "Campaign successfully removed"]})))

(re-frame/reg-event-fx
  ::remove-campaign-error
  (fn [{:keys [db]} [_ {:keys [:campaign/id]} error]]
    {:db (update db :removing-campaign? dissoc id)
     :dispatch-n [[::error-notification/show-error "An error occurs while removing campaign" error]
                  [::logging/error
                   (str "Failed to remove campaign with Id" id)
                   {:error (map :message error)} ::remove-campaign]]}))

(re-frame/reg-event-fx
  ::update-campaign
  (fn [{:keys [db]} [_  {:keys [:form-data] :as data}]]
    (let [id (:id form-data)
          query
          {:queries [[:update-campaign
                      {:campaign/id :$id
                       :user/address :$address
                       :campaign/image :$image
                       :campaign/start-date :$startdate
                       :campaign/end-date :$enddate}]]
           :variables [{:variable/name :$id
                        :variable/type :ID!}
                       {:variable/name :$address
                        :variable/type :ID}
                       {:variable/name :$image
                        :variable/type :String}
                       {:variable/name :$startdate
                        :variable/type :Date}
                       {:variable/name :$enddate
                        :variable/type :Date}]}]
      {:db (assoc-in db [:updating-campaign? id] true)
       :dispatch [::gql-events/mutation
                  {:query query
                   :variables (-> form-data
                                  (select-keys [:id :address :image :start-date :end-date])
                                  (clojure.set/rename-keys {:start-date :startdate
                                                            :end-date :enddate}))
                   :on-success [::update-campaign-success data]
                   :on-error [::update-campaign-error {:campaign/id id}]}]})))

(re-frame/reg-event-fx
  ::update-campaign-success
  (fn [{:keys [db]} [_ args]]
    (let [{:keys [:form-data :on-success]} args]
      (when on-success
        (on-success))
      {:db (update db :updating-campaign? dissoc (:id form-data))
       :dispatch [::notification-events/show "Campaign successfully updated"]})))

(re-frame/reg-event-fx
  ::update-campaign-error
  (fn [{:keys [db]} [_ {:keys [:id]} error]]
    {:db (update db :updating-campaign? dissoc id)
     :dispatch-n [[::error-notification/show-error "An error occurs while updating campaign" error]
                  [::logging/error
                   (str "Failed to update campaign with Id" id)
                   {:error (map :message error)} ::update-campaign]]}))
