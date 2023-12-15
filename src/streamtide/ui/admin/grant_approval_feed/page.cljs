(ns streamtide.ui.admin.grant-approval-feed.page
  "Page to approve the requested grants.
  It shows a list of grants pending to be approved, such that an admin can approve or reject them"
  (:require
    [district.graphql-utils :as gql-utils]
    [district.ui.component.form.input :refer [checkbox-input]]
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.events :as graphql-events]
    [district.ui.graphql.subs :as gql]
    [reagent.core :as r]
    [re-frame.core :refer [subscribe dispatch]]
    [streamtide.ui.admin.grant-approval-feed.events :as gaf-events]
    [streamtide.ui.admin.grant-approval-feed.subs :as gaf-subs]
    [streamtide.ui.components.admin-layout :refer [admin-layout]]
    [streamtide.ui.components.general :refer [nav-anchor no-items-found]]
    [streamtide.ui.components.infinite-scroll :refer [infinite-scroll]]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.components.user :refer [user-photo social-links]]
    [streamtide.ui.utils :as ui-utils]))

(defn content-approval-entry [{:keys [:user/address :user/photo :user/name :user/socials]}
                              {:keys [:grant/request-date :grant/status]}
                              form-data show-rejected?]
  (let [reviewing? @(subscribe [::gaf-subs/reviewing?])
        decision @(subscribe [::gaf-subs/decision address])
        nav (partial nav-anchor {:route :route.profile/index :params {:address address}})
        rejected? (or (= :grant.status/rejected decision)
            (= :grant.status/rejected (gql-utils/gql-name->kw status)))]
    [:div.contentApproval
     {:class (cond
               (or
                 (= :grant.status/approved decision)
                 (and (not show-rejected?)
                    decision))
               "remove"
               (and show-rejected? rejected?)
               "rejected"
               :else nil)}
     [:div.cel.data
      [nav [user-photo {:src photo :class "lb"}]]
      [nav [:h3 name]]]
     [social-links {:socials socials :class "cel"}]
     [:div.cel.date
      [:h4.d-lg-none "Request Date"]
      [:span (ui-utils/format-graphql-time request-date)]]
     [:div.cel.buttons
       [checkbox-input (merge {:form-data form-data
                               :id address}
                              (when reviewing? {:disabled true}))]]
     (when rejected? [:div.rejected-annotation "Rejected"])]))

(def page-size 6)

(defn build-grants-query [{:keys [:statuses]} after]
  (let []
    [:search-grants
     (cond-> {:first page-size
              :statuses statuses
              :order-by :grants.order-by/request-date
              :order-dir :desc}
             after                   (assoc :after after))
     [:total-count
      :end-cursor
      :has-next-page
      [:items [:grant/status
               :grant/request-date
               [:grant/user [:user/address
                             :user/name
                             :user/photo
                             [:user/socials [:social/network
                                             :social/url]]]]]]]]))

(defn grants-entries [query-params grants-search form-data show-rejected?]
  (let [all-grants (->> @grants-search
                        (mapcat (fn [r] (-> r :search-grants :items))))
        loading? (:graphql/loading? (last @grants-search))
        has-more? (-> (last @grants-search) :search-grants :has-next-page)]
    (if (and (empty? all-grants)
             (not loading?))
      [no-items-found]
      [infinite-scroll {:id "approvalfeed"
                        :class "contentApprovalFeed"
                        :fire-tutorial-next-on-items? true
                        :element-height 86
                        :loading? loading?
                        :has-more? has-more?
                        :loading-spinner-delegate (fn []
                                                    [:div.spinner-container [spinner/spin]])
                        :load-fn #(let [{:keys [:end-cursor]} (:search-grants (last @grants-search))]
                                    (dispatch [::graphql-events/query
                                               {:query {:queries [(build-grants-query query-params end-cursor)]}
                                                :id query-params}]))}
       (when-not (:graphql/loading? (first @grants-search))
         (doall
           (for [{:keys [:grant/user] :as grant} all-grants]
             ^{:key (:user/address user)} [content-approval-entry user grant form-data show-rejected?])))])))


(defmethod page :route.admin/grant-approval-feed []
  (let [tx-id (str "add-patrons-" (random-uuid))
        form-data (r/atom {})
        show-rejected-form-data (r/atom {})]
    (fn []
      (let [show-rejected? (:show-rejected @show-rejected-form-data)
            query-params {:statuses (cond-> [:grant.status/requested]
                                            show-rejected? (conj :grant.status/rejected))}
            grants-search (subscribe [::gql/query {:queries [(build-grants-query query-params nil)]}
                                      {:id query-params}])
            reviewing? @(subscribe [::gaf-subs/reviewing?])
            selected-addresses (filter (fn [[_ selected]] selected) @form-data)
            review-button-props (fn [status]
                                  (merge
                                    {:on-click #(dispatch [::gaf-events/review-grant
                                                          {:send-tx/id tx-id
                                                           :user/addresses (keys selected-addresses)
                                                           :grant/status status
                                                           :on-success (fn []
                                                                         (reset! form-data {}))}])}
                                    (when (or reviewing? (empty? selected-addresses)) {:disabled true})))]
        [admin-layout
         [:div.control-header
          [:div.showRejected
           [:label.checkField.simple
            [checkbox-input {:id        :show-rejected
                             :form-data show-rejected-form-data}]
            [:span.checkmark {:class (when show-rejected? "checked")}]
            [:span.text "Show rejected"]]]
          [:div.reviewButtons
           [:button.btBasic.btBasic-light.btApprove
            (review-button-props :grant.status/approved)
            "APPROVE selected"]
           [:button.btBasic.btBasic-light.btDeny
            (review-button-props :grant.status/rejected)
            "DENY selected"]]]
         [:div.headerApprovalFeed.d-none.d-lg-flex
          [:div.cel.cel-data
           [:span.titleCel.col-user "User Profile"]]
          [:div.cel.cel-socials
           [:span.titleCel.col-socials "Socials"]]
          [:div.cel.cel-date
           [:span.titleCel.col-date "Request Date"]]
          [:div.cel.cel-buttons
           [:span.titleCel.col-buttons "Select"]]]
          [grants-entries query-params grants-search form-data show-rejected?]]))))
