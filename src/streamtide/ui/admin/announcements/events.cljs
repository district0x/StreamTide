(ns streamtide.ui.admin.announcements.events
  (:require
    [district.ui.graphql.events :as gql-events]
    [district.ui.logging.events :as logging]
    [district.ui.notification.events :as notification-events]
    [re-frame.core :as re-frame]
    [streamtide.ui.components.error-notification :as error-notification]))

(re-frame/reg-event-fx
  ::add-announcement
  ;Sends a GraphQL mutation request to add an announcement
  (fn [{:keys [db]} [_ {:keys [:form-data :on-success] :as data}]]
    (let [query
          {:queries [[:add-announcement
                      {:announcement/text :$announcement}]]
           :variables [{:variable/name :$announcement
                        :variable/type :String!}]}]
      {:db (assoc db :adding-announcement? true)
       :dispatch [::gql-events/mutation
                  {:query query
                   :variables {:announcement (:announcement form-data)}
                   :on-success [::add-announcement-success {:on-success on-success}]
                   :on-error [::add-announcement-error]}]})))

(re-frame/reg-event-fx
  ::add-announcement-success
  (fn [{:keys [db]} [_ {:keys [:on-success]} result]]
    (when on-success (on-success))
    {:db (dissoc db :adding-announcement?)
     :dispatch [::notification-events/show "Announcement added successfully"]}))

(re-frame/reg-event-fx
  ::add-announcement-error
  (fn [{:keys [db]} [_ error]]
    {:db (dissoc db :adding-announcement?)
     :dispatch-n [[::error-notification/show-error "An error occurs while adding an announcement" error]
                  [::logging/error
                   "Failed to add announcement"
                   {:error (map :message error)} ::add-announcement]]}))

(re-frame/reg-event-fx
  ::remove-announcement
  ;Sends a GraphQL mutation request to remove an existing announcement
  (fn [{:keys [db]} [_ {:keys [:id] :as data}]]
    (let [query
          {:queries [[:remove-announcement
                      {:announcement/id :$id}]]
           :variables [{:variable/name :$id
                        :variable/type :ID!}]}]
      {:db (assoc-in db [:removing-announcement? id] true)
       :dispatch [::gql-events/mutation
                  {:query query
                   :variables {:id id}
                   :on-success [::remove-announcement-success {:announcement/id id}]
                   :on-error [::remove-announcement-error {:announcement/id id}]}]})))

(re-frame/reg-event-fx
  ::remove-announcement-success
  (fn [{:keys [db]} [_ {:keys [:announcement/id]} result]]
    {:db (update db :removing-announcement? dissoc id)
     :dispatch [::notification-events/show "Announcement removed successfully"]}))

(re-frame/reg-event-fx
  ::remove-announcement-error
  (fn [{:keys [db]} [_ {:keys [:announcement/id]} error]]
    {:db (update db :removing-announcement? dissoc id)
     :dispatch-n [[::error-notification/show-error "An error occurs while removing an announcement" error]
                  [::logging/error
                   "Failed to remove announcement"
                   {:error (map :message error)} ::remove-announcement]]}))
