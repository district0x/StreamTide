(ns streamtide.ui.admin.campaigns.page
  "Page to show and manage farcaster campaigns.
  It shows a list of previous campaigns and its details and also allow create new ones"
  (:require
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.events :as graphql-events]
    [district.ui.graphql.subs :as gql]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]
    [streamtide.ui.admin.campaigns.events :as c-events]
    [streamtide.ui.admin.campaigns.subs :as c-subs]
    [streamtide.ui.components.admin-layout :refer [admin-layout]]
    [streamtide.ui.components.custom-select :refer [select]]
    [streamtide.ui.components.general :refer [nav-anchor no-items-found]]
    [streamtide.ui.components.infinite-scroll :refer [infinite-scroll]]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.components.user :refer [user-photo]]
    [streamtide.ui.subs :as st-subs]
    [streamtide.ui.utils :as ui-utils]))

(def page-size 6)

(defn build-campaigns-query [after]
  [:search-campaigns
   (cond-> {:first page-size
            :order-by :campaigns.order-by/id
            :order-dir :desc}
           after (assoc :after after))
   [:total-count
    :end-cursor
    :has-next-page
    [:items [:campaign/id
             :campaign/start-date
             :campaign/end-date
             [:campaign/user [:user/address
                              :user/name
                              :user/photo]]]]]])

(defn build-grants-query []
  [:search-grants
   {:first 100
    :statuses [:grant.status/approved]}
   [[:items [[:grant/user [:user/address
                           :user/name]]]]]])

(defn format-graphql-date [gql-time]
  (when gql-time
    (.toLocaleString (ui-utils/gql-time->date gql-time)
                     js/undefined #js { :year "numeric" :month "short" :day "numeric" } )))

(defn campaign-entry [{:keys [:campaign/id :campaign/start-date :campaign/end-date :campaign/user] :as campaign}]
  (let [nav (partial nav-anchor {:route :route.admin/campaign :params {:campaign id}})]
    [nav
     [:div.contentCampaign
      [:div.cel.id
       [:h4.d-lg-none "ID"]
       [:h4 id]]
      [:div.cel.data
       [:div.d-none.d-lg-block
        [user-photo {:class "lb" :src (:user/photo user)}]]
       [:h4.d-lg-none "Creator"]
       [:h3 (:user/name user)]]
      [:div.cel.startdate
       [:h4.d-lg-none "Start Date"]
       [:span (format-graphql-date start-date)]]
      [:div.cel.enddate
       [:h4.d-lg-none "End Date"]
       [:span (format-graphql-date end-date)]]]]))


(defn campaign-entries [campaigns-search]
  (let [active-session (subscribe [::st-subs/active-session])
        active-account-has-session? (subscribe [::st-subs/active-account-has-session?])
        all-campaigns (->> @campaigns-search
                       (mapcat (fn [r] (-> r :search-campaigns :items)))
                        distinct
                        (sort-by #(int (:campaign/id %)))
                        reverse)
        loading? (:graphql/loading? (last @campaigns-search))
        has-more? (-> (last @campaigns-search) :search-campaigns :has-next-page)]
    (if (and (empty? all-campaigns)
             (not loading?))
      [no-items-found]
      [infinite-scroll {:id "campaigns"
                        :class "contentCampaigns"
                        :fire-tutorial-next-on-items? true
                        :loading? loading?
                        :has-more? has-more?
                        :element-height 86
                        :loading-spinner-delegate (fn []
                                                    [:div.spinner-container [spinner/spin]])
                        :load-fn #(let [{:keys [:end-cursor]} (:search-campaigns (last @campaigns-search))]
                                    (dispatch [::graphql-events/query
                                               {:query {:queries [(build-campaigns-query end-cursor)]}
                                               :id {:id :campaigns
                                                :active-session @active-session
                                                :active-account-has-session? @active-account-has-session?}}]))}
       (when-not (:graphql/loading? (first @campaigns-search))
         (doall
           (for [{:keys [:campaign/id] :as campaign} all-campaigns]
             ^{:key id} [campaign-entry campaign])))])))

(defmethod page :route.admin/campaigns []
  (let [form-data (r/atom {:creator nil})]
    (fn []
      (let [active-session (subscribe [::st-subs/active-session])
            active-account-has-session? (subscribe [::st-subs/active-account-has-session?])
            query-id {:id :campaigns
                      :active-session @active-session
                      :active-account-has-session? @active-account-has-session?}
            campaigns-search (subscribe [::gql/query {:queries [(build-campaigns-query nil)]}
                                         {:id query-id
                                          :refetch-on [::c-events/create-campaign-success]
                                          :refetch-id query-id}])
            creating? @(subscribe [::c-subs/creating-campaign?])
            creator (:creator @form-data)
            grants-search (subscribe [::gql/query {:queries [(build-grants-query)]}
                                      {:id {:id :users
                                            :active-session @active-session
                                            :active-account-has-session? @active-account-has-session?}}])
            users (->> @grants-search
                       (mapcat (fn [r] (-> r :search-grants :items)))
                       (map (fn [grant]
                              (let [user (:grant/user grant)]
                                {:value (:user/address user)
                                 :label (:user/name user)}))))]
        [admin-layout
         [:div.headerCampaign
          [:span.titleCel "Start new Campaign"]
          [:div.form.formCampaigns
           [:div.fieldDescription "Creator"]
           [:div.custom-select.selectForm.creator
            [select {:form-data form-data
                     :id :creator
                     :class "options"
                     :options users}]]
           [:button.btBasic.btBasic-light.btCreateCampaign
            {:on-click #(dispatch [::c-events/create-campaign
                                   {:user/address creator
                                    :on-success (fn [] (reset! form-data {}))}])
             :disabled (or creating? (nil? creator))}
            "Create Campaign"]]]
          [:div.headerCampaign
           [:h3 "Previous Campaigns"]]
          [:div.headerCampaigns.d-none.d-lg-flex
           [:div.cel.cel-id
            [:span.titleCel.col-id "Id"]]
           [:div.cel.cel-creator
            [:span.titleCel.col-eth "Creator"]]
           [:div.cel.cel-startdate
            [:span.titleCel.col-eth "Start Date"]]
           [:div.cel.cel-enddate
            [:span.titleCel.col-eth "End Date"]]]
         [campaign-entries campaigns-search]]))))
