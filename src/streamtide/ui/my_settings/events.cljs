(ns streamtide.ui.my-settings.events
  (:require
    [district.graphql-utils :as gql-utils]
    [district.ui.graphql.events :as gql-events]
    [district.ui.logging.events :as logging]
    [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
  ::save-settings
  ; Send a GraphQL mutation request to save user's settings
  (fn [{:keys [db]} [_ {:keys [:form-data] :as data}]]
    (let [query
          {:queries [[:update-user-info
                      {:input
                        {:user/name :$name
                         :user/description :$description
                         :user/tagline :$tagline
                         :user/handle :$handle
                         :user/url :$url
                         :user/perks :$perks
                         ;; TODO for simplicity we are uploading photos as base64
                         ;; TODO consider using as multipart or using a separated (REST?) API
                         :user/photo :$photo
                         :user/bg-photo :$bgphoto
                         :user/socials :$socials
                         }}
                      [:user/address]]]
           :variables [{:variable/name :$name
                        :variable/type :String}
                       {:variable/name :$description
                        :variable/type :String}
                       {:variable/name :$tagline
                        :variable/type :String}
                       {:variable/name :$handle
                        :variable/type :String}
                       {:variable/name :$url
                        :variable/type :String}
                       {:variable/name :$perks
                        :variable/type :String}
                       {:variable/name :$photo
                        :variable/type :String}
                       {:variable/name :$bgphoto
                        :variable/type :String}
                       {:variable/name :$socials
                        :variable/type (keyword "[SocialLinkInput!]")}
                       ]}]
      {:db (assoc db :uploading-settings true)
       :dispatch [::gql-events/mutation
                  {:query query
                   :variables (-> form-data
                                  (select-keys [:name :description :tagline :handle :url :perks :socials :photo :bg-photo])
                                  (clojure.set/rename-keys {:bg-photo :bgphoto}))
                   :on-success [::save-settings-success]
                   :on-error [::save-settings-error]}]})))

(re-frame/reg-event-fx
  ::save-settings-success
  (fn [{:keys [db]} [_ result]]
    ;; TODO Show message to user
    (js/console.log "SETTING SAVED")
    {:db (dissoc db :uploading-settings)}
    ))

(re-frame/reg-event-fx
  ::save-settings-error
  (fn [{:keys [db]} [_ error]]
    {:db (dissoc db :uploading-settings)
     :dispatch [::logging/error
                "Failed to save settings"
                ;; TODO proper error handling
                {:error (map :message error)} ::save-settings]}))

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
    ;; TODO Show message to user
    (js/console.log "CONTENT UPLOADED")
    (when on-success
      (on-success))
    (merge
      {:db (dissoc db :uploading-content)})))

(re-frame/reg-event-fx
  ::upload-content-error
  (fn [{:keys [db]} [_ form-data error]]
    {:db (dissoc db :uploading-content)
     :dispatch [::logging/error
                "Failed to upload content"
                ;; TODO proper error handling
                {:error (map :message error)
                 :form-data form-data} ::upload-content]}))

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
    ;; TODO Show message to user
    (js/console.log "CONTENT REMOVED")
    (when on-success
      (on-success))
    {:db (-> db
             (update :removing-content dissoc id)
             (assoc-in [:removed-content id] true))}))

(re-frame/reg-event-fx
  ::remove-content-error
  (fn [{:keys [db]} [_ {:keys [:content/id] :as form-data} error]]
    {:db (update db :removing-content dissoc id)
     :dispatch [::logging/error
                "Failed to remove content"
                ;; TODO proper error handling
                {:error (map :message error)
                 :form-data form-data} ::remove-content]}))

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
    ;; TODO Show message to user
    (js/console.log "VISIBILITY CHANGED")
    {:db (update db :setting-visibility dissoc id)}))

(re-frame/reg-event-fx
  ::set-visibility-error
  (fn [{:keys [db]} [_ {:keys [:content/id :on-error] :as form-data} error]]
    (when on-error
      (on-error))
    {:db (update db :setting-visibility dissoc id)
     :dispatch [::logging/error
                "Failed to setting visibility"
                ;; TODO proper error handling
                {:error (map :message error)
                 :form-data form-data} ::set-visibility]}))

(re-frame/reg-event-fx
  ::request-grant
  ; Sends a GraphQL mutation query to request a grant.
  (fn [{:keys [db]} [_ data]]
    (let [query
          {:queries [[:request-grant]]}]
      {:db (assoc db :requesting-grant true)
       :dispatch [::gql-events/mutation
                  {:query query
                   :on-success [::request-grant-success data]
                   :on-error [::request-grant-error]}]})))

(re-frame/reg-event-fx
  ::request-grant-success
  (fn [{:keys [db]} [_ {:keys [:on-success]} result]]
    ;; TODO Show message to user
    (js/console.log "GRANT REQUESTED")
    (when on-success
      (on-success))
    {:db (dissoc db :requesting-grant)}))

(re-frame/reg-event-fx
  ::request-grant-error
  (fn [{:keys [db]} [_ error]]
    {:db (dissoc db :requesting-grant)
     :dispatch [::logging/error
                 "Failed to request grant"
                 ;; TODO proper error handling
                 {:error (map :message error)} ::request-grant]}))
