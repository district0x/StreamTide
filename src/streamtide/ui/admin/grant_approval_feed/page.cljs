(ns streamtide.ui.admin.grant-approval-feed.page
  "Page to approve the requested grants.
  It shows a list of grants pending to be approved, such that an admin can approve or reject them"
  (:require
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.events :as graphql-events]
    [district.ui.graphql.subs :as gql]
    [district.ui.web3-tx-id.subs :as tx-id-subs]
    [re-frame.core :refer [subscribe dispatch]]
    [streamtide.ui.admin.grant-approval-feed.events :as gaf-events]
    [streamtide.ui.admin.grant-approval-feed.subs :as gaf-subs]
    [streamtide.ui.components.admin-layout :refer [admin-layout]]
    [streamtide.ui.components.general :refer [nav-anchor no-items-found]]
    [streamtide.ui.components.infinite-scroll :refer [infinite-scroll]]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.components.user :refer [user-photo social-links]]))

(defn content-approval-entry [{:keys [:user/address :user/photo :user/name :user/socials]}]
  (let [tx-id (str "add-patron_" address)
        loading? (or @(subscribe [::gaf-subs/reviewing? address])
                     @(subscribe [::tx-id-subs/tx-pending? {:streamtide/add-patron tx-id}]))
        decision? (or @(subscribe [::gaf-subs/decision? address])
                      @(subscribe [::tx-id-subs/tx-success? {:streamtide/add-patron tx-id}]))
        nav (partial nav-anchor {:route :route.profile/index :params {:address address}})]
    [:div.contentApproval
     (when decision? {:class "remove"})
     [:div.cel.data
      [nav [user-photo {:src photo :class "lb"}]]
      [nav [:h3 name]]]
     [social-links {:socials socials :class "cel"}]
     [:hr.d-lg-none]
     [:div.cel.buttons
      (when loading? {:class "loading"})
      [:button.btBasic.btBasic-light.btApprove
       (merge {:on-click
        #(dispatch [::gaf-events/review-grant {:send-tx/id tx-id
                                               :user/address address
                                               :grant/status :grant.status/approved}])
        } (when loading? {:disabled true})) "APPROVE"]
      [:button.btBasic.btBasic-light.btDeny
       (merge {:on-click
        #(dispatch [::gaf-events/review-grant {:user/address address
                                               :grant/status :grant.status/rejected}])
        } (when loading? {:disabled true}) ) "DENY"]]]))

(def page-size 6)

;; TODO maybe move this to a common ns?
(defn build-grants-query [{:keys [:statuses]} after]
  (let []
    [:search-grants
     (cond-> {:first page-size
              :statuses [:grant.status/requested]
              :order-by :grants.order-by/request-date
              :order-dir :desc}
             after                   (assoc :after after))
     [:total-count
      :end-cursor
      :has-next-page
      [:items [:grant/status
               [:grant/user [:user/address
                             :user/name
                             :user/photo
                             [:user/socials [:social/network
                                             :social/url]]]]]]]]))

(defn grants-entries [query-params grants-search]
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
           (for [{:keys [:grant/user]} all-grants]
             ^{:key (:user/address user)} [content-approval-entry user])))])))


(defmethod page :route.admin/grant-approval-feed []
  (let [query-params {:statuses [:grant.status/requested]}
        grants-search (subscribe [::gql/query {:queries [(build-grants-query query-params nil)]}
                                  {:id query-params}])]
    (fn []
      [admin-layout
       [:div.headerApprovalFeed.d-none.d-md-flex
        [:div.cel.cel-data
         [:span.titleCel.col-user "User Profile"]]
        [:div.cel.cel-socials
         [:span.titleCel.col-socials "Socials"]]
        [:div.cel.cel-buttons
         [:span.titleCel.col-buttons "Approve / Deny"]]]
        [grants-entries query-params grants-search]])))
