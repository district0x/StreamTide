(ns streamtide.ui.my-content.events
  (:require
    [district.graphql-utils :as gql-utils]
    [district.ui.graphql.events :as gql-events]
    [district.ui.logging.events :as logging]
    [district.ui.notification.events :as notification-events]
    [re-frame.core :as re-frame]))


(re-frame/reg-event-fx
  ::upload-content
  ; Sends a GraphQL mutation request to upload content (i.e., a link)
  (fn [{:keys [db]} [_ {:keys [:url :type] :as data}]]
    (let [query
          {:queries [[:add-content
                      {:content/type :$type
                       :content/url :$url
                       :content/public false}]]
           :variables [{:variable/name :$type
                        :variable/type :ContentType!}
                       {:variable/name :$url
                        :variable/type :String!}]}]
      {:db (assoc db :uploading-content true)
       :dispatch [::gql-events/mutation
                  {:query query
                   :variables {:url url
                               :type (gql-utils/kw->gql-name (keyword :content-type type))}
                   :on-success [::upload-content-success data]
                   :on-error [::upload-content-error data]}]})))

(re-frame/reg-event-fx
  ::upload-content-success
  (fn [{:keys [db]} [_ {:keys [:on-success]} result]]
    (when on-success
      (on-success))
    (merge
      {:db (dissoc db :uploading-content)
       :dispatch [::notification-events/show "Content added successfully"]})))

(re-frame/reg-event-fx
  ::upload-content-error
  (fn [{:keys [db]} [_ form-data error]]
    {:db (dissoc db :uploading-content)
     :dispatch-n [[::logging/error
                   "Failed to upload content"
                   {:error (map :message error)
                    :form-data form-data} ::upload-content]
                  [::notification-events/show "[ERROR] An error occurs while adding content"]]}))

(re-frame/reg-event-fx
  ::remove-content
  ; Sends a GraphQL request to remove a piece of content
  (fn [{:keys [db]} [_ {:keys [:content/id] :as data}]]
    (let [query
          {:queries [[:remove-content
                      {:content/id :$id}]]
           :variables [{:variable/name :$id
                        :variable/type :ID!}]}]
      {:db (assoc-in db [:removing-content id] true)
       :dispatch [::gql-events/mutation
                  {:query query
                   :variables {:id id}
                   :on-success [::remove-content-success data]
                   :on-error [::remove-content-error data]}]})))

(re-frame/reg-event-fx
  ::remove-content-success
  (fn [{:keys [db]} [_ {:keys [:content/id :on-success]} result]]
    (when on-success
      (on-success))
    {:db (-> db
             (update :removing-content dissoc id)
             (assoc-in [:removed-content id] true))}))

(re-frame/reg-event-fx
  ::remove-content-error
  (fn [{:keys [db]} [_ {:keys [:content/id] :as form-data} error]]
    {:db (update db :removing-content dissoc id)
     :dispatch-n [[::logging/error
                   "Failed to remove content"
                   {:error (map :message error)
                    :form-data form-data} ::remove-content]
                  [::notification-events/show "[ERROR] An error occurs while removing content"]]}))

(re-frame/reg-event-fx
  ::content-added
  ; Event triggered when a content is added. This is an empty event just aimed for subscription
  (constantly nil))

(re-frame/reg-event-fx
  ::content-removed
  ; Event triggered when a content is removed. This is an empty event just aimed for subscription
  (constantly nil))

(re-frame/reg-event-fx
  ::set-visibility
  ; Send a GraphQL mutation request to change the visibility (public/private) of a piece of content
  (fn [{:keys [db]} [_ {:keys [:content/id :content/public :on-error] :as data}]]
    (let [query
          {:queries [[:set-content-visibility
                      {:content/id :$id
                       :content/public :$public}]]
           :variables [{:variable/name :$id
                        :variable/type :ID!}
                       {:variable/name :$public
                        :variable/type :Boolean!}]}]
      {:db (assoc-in db [:setting-visibility id] true)
       :dispatch [::gql-events/mutation
                  {:query query
                   :variables {:id id
                               :public public}
                   :on-success [::set-visibility-success data]
                   :on-error [::set-visibility-error data]}]})))

(re-frame/reg-event-fx
  ::set-visibility-success
  (fn [{:keys [db]} [_ {:keys [:content/id]} result]]
    {:db (update db :setting-visibility dissoc id)}))

(re-frame/reg-event-fx
  ::set-visibility-error
  (fn [{:keys [db]} [_ {:keys [:content/id :on-error] :as form-data} error]]
    (when on-error
      (on-error))
    {:db (update db :setting-visibility dissoc id)
     :dispatch-n [[::logging/error
                   "Failed to setting visibility"
                   {:error (map :message error)
                    :form-data form-data} ::set-visibility]
                  [::notification-events/show "[ERROR] An error occurs while updating visibility"]]}))
