(ns streamtide.ui.admin.campaign.page
  "Page to manage a specific campaign"
  (:require
    [cljsjs.bignumber]
    [district.ui.component.form.input :refer [file-drag-input]]
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.subs :as gql]
    [district.ui.router.events :as router-events]
    [district.ui.router.subs :as router-subs]
    [reagent.ratom :refer [reaction]]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]
    [streamtide.ui.admin.campaign.events :as c-events]
    [streamtide.ui.admin.campaign.subs :as c-subs]
    [streamtide.ui.components.app-layout :refer [app-layout]]
    [streamtide.ui.components.custom-select :refer [select]]
    [streamtide.ui.components.error-notification :as error-notification]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.components.warn-popup :as warn-popup]
    [streamtide.ui.subs :as st-subs]
    [streamtide.ui.utils :as ui-utils]
    [taoensso.timbre :as log]))

(def max-filesize 10485760)

(defn build-campaign-info-query [{:keys [:campaign/id]}]
  [:campaign
   {:campaign/id id}
   [:campaign/start-date
    :campaign/end-date
    :campaign/image
    [:campaign/user [:user/address
                     :user/photo
                     :user/name]]]])

(defn build-grants-query []
  [:search-grants
   {:first 100
    :statuses [:grant.status/approved]}
   [[:items [[:grant/user [:user/address
                           :user/name]]]]]])

(defn- campaign-picture-edit [{:keys [:form-data :initial-value :id :errors]}]
  [file-drag-input {:form-data form-data
                    :group-class "photo"
                    :id id
                    :img-attributes {:src (or (-> @form-data id :selected-file :url-data) initial-value)}
                    :label nil
                    :accept "image/png,image/gif,image/jpeg"
                    :errors errors
                    :file-accept-pred (fn [{:keys [name type size] :as props}]
                                        (and (#{"image/png" "image/gif" "image/jpeg"} type)
                                             (< size max-filesize)))
                    :on-file-accepted (fn [{:keys [name type size array-buffer] :as props}]
                                        (swap! form-data update-in [id] dissoc :error)
                                        (reset! form-data (with-meta @form-data {:touched? true}))
                                        (log/info "Accepted file" {:name name :type type :size size} ::file-accepted))
                    :on-file-rejected (fn [{:keys [name type size] :as props}]
                                        (swap! form-data assoc id {:error (if (>= size max-filesize)
                                                                            "File too large (> 10MB)"
                                                                            "Non .png .jpeg .gif file selected")})
                                        (reset! form-data (with-meta @form-data {:touched? true}))
                                        (log/warn "Rejected file" {:name name :type type :size size} ::file-rejected))}])

(defn- photo->gql [photo]
  (-> photo :selected-file :url-data))

(defn- clean-form-data [form-data]
  (try
    (cond-> form-data
            (and (:image form-data) (-> form-data :image :error)) (dissoc :image)
            (and (:image form-data) (-> form-data :image :error not)) (update :image photo->gql))
    (catch :default e
      (dispatch [::error-notification/show-error "Invalid data" e])
      (throw e))))


(defmethod page :route.admin/campaign []
  (let [active-page-sub (subscribe [::router-subs/active-page])
        campaign-id (-> @active-page-sub :params :campaign)
        form-data (r/atom {:id campaign-id})
        errors (reaction {:local (cond-> {}
                                         )})]
    (fn []
      (let [active-session (subscribe [::st-subs/active-session])
            active-account-has-session? (subscribe [::st-subs/active-account-has-session?])
            campaign-info-query (when @active-account-has-session?
                                  (subscribe [::gql/query {:queries [(build-campaign-info-query {:campaign/id campaign-id})]}]))
            loading? (or (nil? campaign-info-query) (:graphql/loading? @campaign-info-query))
            grants-search (when @active-account-has-session?
                            (subscribe [::gql/query {:queries [(build-grants-query)]}
                                        {:id {:id :users
                                              :active-session @active-session
                                              :active-account-has-session? @active-account-has-session?}}]))
            users (when grants-search
                    (->> @grants-search
                         (mapcat (fn [r] (-> r :search-grants :items)))
                         (map (fn [grant]
                                (let [user (:grant/user grant)]
                                  {:value (:user/address user)
                                   :label (:user/name user)})))))
            updating? @(subscribe [::c-subs/updating-campaign? campaign-id])
            removing? @(subscribe [::c-subs/removing-campaign? campaign-id])
            campaign (when campaign-info-query (:campaign @campaign-info-query))
            {:keys [:campaign/start-date :campaign/end-date :campaign/image :campaign/user]} campaign
            url (.-href (js/URL. (str "/frame/campaign/" campaign-id) js/window.location.origin))]
        [app-layout
         [:main.pageSite.pageCampaign
          {:id "campaign"}
          [:div.container
           [:h1.titlePage "Campaign"]
           [:div.headerCampaign
            [:h2 (str "Campaign: " campaign-id)]
            [:div
             [:span "Frame URL:"]
             [:span url]
             [:img.clipboard {:height "24px"
                    :src "/img/layout/icon-clipboard.svg"
                    :on-click #(js/navigator.clipboard.writeText url)}]]]
           (if loading?
             [spinner/spin]
             [:div.contentCampaign
              [:div.image
               [campaign-picture-edit
                {:form-data form-data
                 :initial-value image
                 :id :image
                 :errors errors}]]
              [:div.creator-container
               [:label
                [:span "Creator"]]
               [:div.custom-select.selectForm.creator
                [select {:form-data form-data
                         :id :address
                         :class "options"
                         :initial-value (:user/address user)
                         :options users}]]]
              ; TODO allow defining dates
              ;[:div.start (str "Start Time: " (ui-utils/format-graphql-time start-date))]
              ;[:div.end (str "End Time: " (ui-utils/format-graphql-time end-date))]
              [:div.buttons
                [:button.btBasic.btBasic-light.btUpdateCampaign
                 {:on-click #(dispatch [::c-events/update-campaign
                                        {:form-data (clean-form-data @form-data)
                                         :on-success (fn []
                                                       (reset! form-data (with-meta @form-data {:touched? false})))}])
                  :disabled (or (not (:touched? (meta @form-data))) updating? removing?)}
                 "Update"]
                [:button.btBasic.btBasic-light.btDeleteCampaign
                 {:on-click (fn [e]
                              (.stopPropagation e)
                              (dispatch [::warn-popup/show-popup
                                         {:on-accept [::c-events/remove-campaign
                                                      {:campaign/id campaign-id
                                                       :on-success (fn []
                                                                     (dispatch [::router-events/navigate :route.admin/campaigns]))}]}]))
                  :disabled (or updating? removing?)}
                 "Delete"]]])]
          [warn-popup/warn-popup {:title "Delete Campaign"
                                  :content "Are you sure you want to delete this campaign?"
                                  :button-label "Delete"
                                  :cancel-button? true}]]]))))
