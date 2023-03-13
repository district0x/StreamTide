(ns streamtide.ui.admin.announcements.events
  (:require
    [district.ui.graphql.events :as gql-events]
    [district.ui.logging.events :as logging]
    [re-frame.core :as re-frame]))

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
    ;; TODO Show message to user
    (js/console.log "ANNOUNCEMENT ADDED")
    (when on-success (on-success))
    {:db (dissoc db :adding-announcement?)}))

(re-frame/reg-event-fx
  ::add-announcement-error
  (fn [{:keys [db]} [_ error]]
    {:db (dissoc db :adding-announcement?)
     :dispatch [::logging/error
                (str "Failed to add announcement")
                ;; TODO proper error handling
                {:error (map :message error)} ::add-announcement]}))

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
    ;; TODO Show message to user
    (js/console.log "ANNOUNCEMENT REMOVED")
    {:db (update db :removing-announcement? dissoc id)}))

(re-frame/reg-event-fx
  ::remove-announcement-error
  (fn [{:keys [db]} [_ {:keys [:announcement/id]} error]]
    {:db (update db :removing-announcement? dissoc id)
     :dispatch [::logging/error
                (str "Failed to remove announcement")
                ;; TODO proper error handling
                {:error (map :message error)} ::remove-announcement]}))
