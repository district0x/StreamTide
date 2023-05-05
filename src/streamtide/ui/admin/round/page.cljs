(ns streamtide.ui.admin.round.page
  "Page to manage a specific round "
  (:require
    [district.graphql-utils :as gql-utils]
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.events :as graphql-events]
    [district.ui.graphql.subs :as gql]
    [district.ui.router.subs :as router-subs]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]
    [streamtide.shared.utils :as shared-utils]
    [streamtide.ui.components.app-layout :refer [app-layout]]
    [streamtide.ui.components.general :refer [nav-anchor no-items-found]]
    [streamtide.ui.components.infinite-scroll :refer [infinite-scroll]]
    [streamtide.ui.components.search :refer [search-tools]]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.components.user :refer [user-photo]]
    [streamtide.ui.utils :as ui-utils]))

(def page-size 6)

(defn build-round-info-query [{:keys [:round/id]}]
  [:round
   {:round/id id}
   [:round/start
    :round/duration
    :round/matching-pool
    :round/distributed]])

(def donations-order [{:key "amount/desc" :value "Granted Higher"}
                      {:key "amount/asc" :value "Granted Lower"}
                      {:key "date/desc" :value "Newest"}
                      {:key "date/asc" :value "Oldest"}
                      {:key "username/asc" :value "Artist Name"}])

(defn build-donations-query [{:keys [:round :search-term :order-key]} after]
  (let [[order-by order-dir] ((juxt namespace name) (keyword order-key))]
    [:search-donations
     (cond-> {:first page-size
              :round round}
             (not-empty search-term) (assoc :search-term search-term)
             after                   (assoc :after after)
             order-by                (assoc :order-by (keyword "donations.order-by" order-by))
             order-dir               (assoc :order-dir (keyword order-dir)))
     [:total-count
      :end-cursor
      :has-next-page
      [:items [:donation/id
               :donation/date
               :donation/amount
               :donation/coin
               [:donation/receiver [:user/address
                                    :user/name
                                    :user/photo]]
               [:donation/sender [:user/address
                                  :user/name]]]]]]))

(defn round-open? [round]
  (let [{:keys [:round/start :round/duration]} round
        start (.getTime (gql-utils/gql-date->date start))]
  (< (+ start (* 1000 duration)) (shared-utils/now))))

(defn donation-entry [{:keys [:donation/receiver :donation/sender :donation/amount :donation/date]}]
  (let [nav-receiver (partial nav-anchor {:route :route.profile/index :params {:address (:user/address receiver)}})
        nav-sender (partial nav-anchor {:route :route.profile/index :params {:address (:user/address sender)}})]
    [:div.donation
     [nav-receiver [user-photo {:class "lb" :src (:user/photo receiver)}]]
     [:div.data
      [nav-receiver [:h3 (:user/name receiver)]]]
     [:ul.score
      [:li
      ; TODO cut addresses
       [:span [nav-sender [:span (or (:user/name sender) (:user/address sender))]]]]
      [:li
       [:span (ui-utils/format-graphql-time date)]]
      [:li
       ;; TODO format amount (wei->eth)
       [:span amount]]]]))

(defn donations-entries [form-data donations-search]
  (let [all-donations (->> @donations-search
                           (mapcat (fn [r] (-> r :search-donations :items))))
        loading? (:graphql/loading? (last @donations-search))
        has-more? (-> (last @donations-search) :search-donations :has-next-page)]
    (if (and (empty? all-donations)
             (not loading?))
      [no-items-found]
      [infinite-scroll {:class "donations"
                        :fire-tutorial-next-on-items? true
                        :loading? loading?
                        :has-more? has-more?
                        :element-height 88
                        :loading-spinner-delegate (fn []
                                                    [:div.spinner-container [spinner/spin]])
                        :load-fn #(let [{:keys [:end-cursor]} (:search-leaders (last @donations-search))]
                                    (dispatch [::graphql-events/query
                                               {:query {:queries [(build-donations-query @form-data end-cursor)]}
                                                :id @form-data}]))}
       (when-not (:graphql/loading? (first @donations-search))
         (doall
           (for [donation all-donations]
             ^{:key (-> donation :donation/id)} [donation-entry donation])))])))

(defn donations [round-id]
  (let [form-data (r/atom {:round round-id
                           :search-term ""
                           :order-key (:key (first donations-order))})]
    (fn []
      (let [donations-search (subscribe [::gql/query {:queries [(build-donations-query @form-data nil)]}
                                       {:id @form-data}])]
        [:div.contentDonation
         [:h2 "Donations"]
         [search-tools {:form-data form-data
                        :search-id :search-term
                        :select-options donations-order}]
         [:div.headerDonations.d-none.d-md-flex
          [:div.cel-data
           [:span.titleCel.col-user "Receiver"]]
          [:div.cel-score
           [:span.titleCel.col-user "Sender"]
           [:span.titleCel.col-date "Date"]
           [:span.titleCel.col-amount "Amount"]]]
         [donations-entries form-data donations-search]]))))


(defmethod page :route.admin/round []
  (let [active-page-sub (subscribe [::router-subs/active-page])
        round-id (-> @active-page-sub :params :round)]
    (fn []
      (let [round-info-query (subscribe [::gql/query {:queries [(build-round-info-query {:round/id round-id})]}])
            loading? (:graphql/loading? @round-info-query)
            round (:round @round-info-query)
            {:keys [:round/start :round/duration :round/matching-pool :round/distributed]} round]
        [app-layout
         [:main.pageSite.pageRound
          {:id "round"}
          [:div.container
           [:h1.titlePage "Round"]
           [:div.headerRound
            [:h2 (str "Round: " round-id)]]
           (if loading?
             [spinner/spin]
             [:div.contentRound
              (let [status (if (round-open? round) "open" (if distributed "distributed" "closed"))]
                [:div.status
                 {:class status}
                 (str "Status: " status)])
              [:div.start (str "Start Time: " (ui-utils/format-graphql-time start))]
              [:div.end (str "End Time: " (ui-utils/format-graphql-time (+ start duration)))]
              [:div.matching (str "Matching pool: " matching-pool " ETH")]
              [donations round-id]])]]]))))
