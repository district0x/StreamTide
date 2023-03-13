(ns streamtide.ui.leaderboard.page
  "Page showing a list of top donations"
  (:require
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.events :as graphql-events]
    [district.ui.graphql.subs :as gql]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]
    [streamtide.ui.components.app-layout :refer [app-layout]]
    [streamtide.ui.components.general :refer [nav-anchor no-items-found]]
    [streamtide.ui.components.infinite-scroll :refer [infinite-scroll]]
    [streamtide.ui.components.search :refer [search-tools]]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.components.user :refer [user-photo]]
    [streamtide.ui.utils :as ui-utils]))

(def page-size 6)

(def donations-order [{:key "date/desc" :value "Newest"}
                      {:key "date/asc" :value "Oldest"}
                      {:key "username/asc" :value "Artist Name"}
                      {:key "granted-amount/desc" :value "Granted Higher"}
                      {:key "granted-amount/asc" :value "Granted Lower"}
                      {:key "matching-amount/desc" :value "Matching Higher"}
                      {:key "matching-amount/asc" :value "Matching Lower"}
                      {:key "total-amount/desc" :value "Total Higher"}
                      {:key "total-amount/asc" :value "Total Lower"}])

(defn build-donations-query [{:keys [:search-term :order-key]} after]
  (let [[order-by order-dir] ((juxt namespace name) (keyword order-key))]
    [:search-donations
     (cond-> {:first page-size}
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
               :donation/matching
               :donation/coin
               [:donation/receiver [:user/address
                                    :user/name
                                    :user/photo]]]]]]))

(defn leaderboard-entry [{:keys [:donation/receiver :donation/date :donation/amount :donation/matching]}]
  (let [nav (partial nav-anchor {:route :route.profile/index :params {:address (:user/address receiver)}})]
    [:div.leaderboard
     [nav [user-photo {:class "lb" :src (:user/photo receiver)}]]
     [:div.data
      [nav [:h3 (:user/name receiver)]]
      [:span (ui-utils/format-graphql-time date)]]
     [:ul.score
      [:li
       ;; TODO format amount (wei->eth)
       [:span amount]]
      [:li
       [:span matching]]
      [:li
       [:span (+ amount matching)]]]]))

(defn leaderboard-entries [form-data donations-search]
  (let [all-donations (->> @donations-search
                           (mapcat (fn [r] (-> r :search-donations :items))))
        loading? (:graphql/loading? (last @donations-search))
        has-more? (-> (last @donations-search) :search-donations :has-next-page)]
    (if (and (empty? all-donations)
             (not loading?))
      [no-items-found]
      [infinite-scroll {:class "leaderboards"
                        :fire-tutorial-next-on-items? true
                        :loading? loading?
                        :has-more? has-more?
                        :element-height 88
                        :loading-spinner-delegate (fn []
                                                    [:div.spinner-container [spinner/spin]])
                        :load-fn #(let [{:keys [:end-cursor]} (:search-donations (last @donations-search))]
                                    (dispatch [::graphql-events/query
                                               {:query {:queries [(build-donations-query @form-data end-cursor)]}
                                                :id @form-data}]))}
       (when-not (:graphql/loading? (first @donations-search))
         (doall
           (for [donation all-donations]
             ^{:key (:donation/id donation)} [leaderboard-entry donation])))])))

(defmethod page :route.leaderboard/index []
  (let [form-data (r/atom {:search-term ""
                           :order-key (:key (first donations-order))})]
    (fn []
      (let [donations-search (subscribe [::gql/query {:queries [(build-donations-query @form-data nil)]}
                                         {:id @form-data}])]
        [app-layout
         [:main.pageSite
          {:id "leaderboard"}
          [:div.headerLeaderboard
           [:div.container
            [:h1.titlePage "Leaderboard"]
            [search-tools {:form-data form-data
                           :search-id :search-term
                           :select-options donations-order}]]]
          [:div.contentLeaderboard.container
           [:div.headerLeaderboards.d-none.d-md-flex
            [:div.cel-data
             [:span.titleCel.col-user "Artist Name"]
             [:span.titleCel.col-date "Date"]
             ]
            [:div.cel-score
             [:span.titleCel.col-amount "Amount Granted"]
             [:span.titleCel.col-matching "Matching Received"]
             [:span.titleCel.col-total "Total Received"]]]
           [leaderboard-entries form-data donations-search]
           ;[:div.page-load-status]
           ]]]))))
