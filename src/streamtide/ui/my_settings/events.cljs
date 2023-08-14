(ns streamtide.ui.my-settings.events
  (:require
    [district.ui.graphql.events :as gql-events]
    [district.ui.logging.events :as logging]
    [district.ui.notification.events :as notification-events]
    [re-frame.core :as re-frame]
    [streamtide.ui.components.verifiers :as verifiers]))

(re-frame/reg-event-fx
  ::do-save-settings
  ; Send a GraphQL mutation request to save user's settings
  (fn [{:keys [db]} [_ {:keys [:form-data :on-success :on-error] :as data}]]
    (if (empty? form-data)
      {:dispatch (conj on-error [{:message "No settings to save"}])}
      (let [query
            {:queries [[:update-user-info
                        {:input
                          {:user/name :$name
                           :user/description :$description
                           :user/tagline :$tagline
                           :user/handle :$handle
                           :user/url :$url
                           :user/perks :$perks
                           :user/min-donation :$mindonation
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
                         {:variable/name :$mindonation
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
                                    (select-keys [:name :description :tagline :handle :url :perks :min-donation :socials :photo :bg-photo])
                                    (clojure.set/rename-keys {:bg-photo :bgphoto :min-donation :mindonation}))
                     :on-success on-success
                     :on-error on-error}]}))))

(re-frame/reg-event-fx
  ::save-settings
  (fn [{:keys [db]} [_ {:keys [:form-data] :as data}]]
    {:dispatch [::do-save-settings {:form-data form-data
                                    :on-success [::save-settings-success data]
                                    :on-error [::save-settings-error]}]}))

(re-frame/reg-event-fx
  ::save-settings-success
  (fn [{:keys [db]} [_ {:keys [:on-success]} result]]
    (when on-success
      (on-success))
    {:db (dissoc db :uploading-settings)
     :dispatch [::notification-events/show "Settings saved successfully"]}))

(re-frame/reg-event-fx
  ::save-settings-error
  (fn [{:keys [db]} [_ error]]
    {:db (dissoc db :uploading-settings)
     :dispatch-n [[::notification-events/show "[ERROR] An error occurs while trying to save settings"]
                  [::logging/error
                   "Failed to save settings"
                   {:error (map :message error)} ::save-settings]]}))

(re-frame/reg-event-fx
  ::save-and-request-grant
  ; Save the current settings and sends a GraphQL mutation query to request a grant.
  (fn [{:keys [db]} [_ {:keys [:form-data :on-success] :as data}]]
    {:dispatch [::do-save-settings {:form-data form-data
                                    :on-success [::request-grant {:on-success on-success}]
                                    :on-error [::request-grant-error]}]}))

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
    (when on-success
      (on-success))
    {:db (dissoc db :requesting-grant)
     :dispatch [::notification-events/show "Grant has been requested"]}))

(re-frame/reg-event-fx
  ::request-grant-error
  (fn [{:keys [db]} [_ error]]
    {:db (dissoc db :requesting-grant)
     :dispatch-n [[::notification-events/show "[ERROR] An error occurs while requesting grant"]
                  [::logging/error
                   "Failed to request grant"
                   {:error (map :message error)} ::request-grant]]}))

(re-frame/reg-event-fx
  ::verify-social
  ; Starts the process to verify a social link
  (fn [{:keys [db]} [_ {:keys [:social/network] :as data}]]
    {:db (assoc-in db [:verifying-social network] true)
     :verifiers/verify-social {:social/network network
                               :on-success [::verify-social-success data]
                               :on-error [::verify-social-error data]}}))

(re-frame/reg-event-fx
  ::verify-social-success
  (fn [{:keys [db]} [_ {:keys [:social/network :on-success] :as args} result]]
    (when on-success
      (on-success))
    {:db (update db :verifying-social dissoc network)
     :dispatch [::notification-events/show "Social account verified"]}))

(re-frame/reg-event-fx
  ::verify-social-error
  (fn [{:keys [db]} [_ {:keys [:social/network :on-error] :as form-data} error]]
    (when on-error
      (on-error))
    {:db (update db :verifying-social dissoc network)
     :dispatch-n [[::notification-events/show "[ERROR] An error occurs while verifying your social account"]
                  [::logging/error
                   "Failed to verify social"
                   {:error error
                    :form-data form-data} ::verify-social]]}))
