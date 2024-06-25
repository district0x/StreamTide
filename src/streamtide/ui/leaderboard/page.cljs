(ns streamtide.ui.leaderboard.page
  "Page showing a list of top donations and matchings"
  (:require
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.events :as graphql-events]
    [district.ui.graphql.subs :as gql]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]
    [streamtide.shared.utils :as shared-utils]
    [streamtide.ui.components.app-layout :refer [app-layout]]
    [streamtide.ui.components.general :refer [nav-anchor no-items-found]]
    [streamtide.ui.components.infinite-scroll :refer [infinite-scroll]]
    [streamtide.ui.components.search :refer [search-tools]]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.components.user :refer [user-photo]]
    [streamtide.ui.components.custom-select :refer [select]]
    [streamtide.ui.subs :as st-subs]))

(def page-size 6)

(def leaders-order [{:value "total-amount/desc" :label "Total Higher"}
                    {:value "total-amount/asc" :label "Total Lower"}
                    {:value "donation-amount/desc" :label "Granted Higher"}
                    {:value "donation-amount/asc" :label "Granted Lower"}
                    {:value "matching-amount/desc" :label "Matching Higher"}
                    {:value "matching-amount/asc" :label "Matching Lower"}
                    {:value "username/asc" :label "Artist Name"}])

(defn build-leaders-query [{:keys [:search-term :order-key :round]} after]
  (let [[order-by order-dir] ((juxt namespace name) (keyword order-key))]
    [:search-leaders
     (cond-> {:first page-size}
             (not-empty search-term) (assoc :search-term search-term)
             after                   (assoc :after after)
             order-by                (assoc :order-by (keyword "leaders.order-by" order-by))
             order-dir               (assoc :order-dir (keyword order-dir))
             (and round (not= round "overall")) (assoc :round (str round)))
     [:total-count
      :end-cursor
      :has-next-page
      [:items [:leader/donation-amount
               [:leader/matching-amounts [:amount
                                          [:coin [:coin/symbol
                                                  :coin/decimals]]]]
               [:leader/total-amounts [:amount
                                       [:coin [:coin/symbol
                                               :coin/decimals]]]]
               [:leader/receiver [:user/address
                                  :user/name
                                  :user/photo
                                  :user/unlocked]]]]]]))

(defn build-rounds-query []
  [:search-rounds
   {:first 100}
   [[:items [:round/id]]]])

(defn leaderboard-entry [{:keys [:leader/receiver :leader/donation-amount :leader/matching-amounts :leader/total-amounts]}]
  (let [nav (partial nav-anchor {:route :route.profile/index :params {:address (:user/address receiver)}})]
    [:div.leaderboard
     [nav [user-photo {:class (str "lb" (when (:user/unlocked receiver) " star")) :src (:user/photo receiver)}]]
     [:div.data
      [nav [:h3 (:user/name receiver)]]]
     [:ul.score
      [:li
       [:h4.d-md-none "Amount Granted"]
       [:span (shared-utils/format-price donation-amount {:coin/decimals 18 :coin/symbol "ETH"})]]
      [:li
       [:h4.d-md-none "Matching Received"]

       [:div.amounts
        (map (fn [mp]
               [:span {:key (-> mp :coin :coin/symbol)} (shared-utils/format-price (:amount mp) (:coin mp))]) matching-amounts)]]
      [:li
       [:h4.d-md-none "Total Received"]
       [:div.amounts
        (map (fn [mp]
               [:span {:key (-> mp :coin :coin/symbol)} (shared-utils/format-price (:amount mp) (:coin mp))]) total-amounts)]]]]))

(defn leaderboard-entries [form-data leaders-search]
  (let [active-session (subscribe [::st-subs/active-session])
        active-account-has-session? (subscribe [::st-subs/active-account-has-session?])
        all-leaders (->> @leaders-search
                           (mapcat (fn [r] (-> r :search-leaders :items))))
        loading? (:graphql/loading? (last @leaders-search))
        has-more? (-> (last @leaders-search) :search-leaders :has-next-page)]
    (if (and (empty? all-leaders)
             (not loading?))
      [no-items-found]
      [infinite-scroll {:class "leaderboards"
                        :fire-tutorial-next-on-items? true
                        :loading? loading?
                        :has-more? has-more?
                        :element-height 88
                        :loading-spinner-delegate (fn []
                                                    [:div.spinner-container [spinner/spin]])
                        :load-fn #(let [{:keys [:end-cursor]} (:search-leaders (last @leaders-search))]
                                    (dispatch [::graphql-events/query
                                               {:query {:queries [(build-leaders-query @form-data end-cursor)]}
                                                :id (merge @form-data {:active-session @active-session
                                                                       :active-account-has-session? @active-account-has-session?})}]))}
       (when-not (:graphql/loading? (first @leaders-search))
         (doall
           (for [leader all-leaders]
             ^{:key (-> leader :leader/receiver :user/address)} [leaderboard-entry leader])))])))

(defmethod page :route.leaderboard/index []
  (let [form-data (r/atom {:search-term ""
                           :order-key (:value (first leaders-order))
                           :round "overall"})]
    (fn []
      (let [active-session (subscribe [::st-subs/active-session])
            active-account-has-session? (subscribe [::st-subs/active-account-has-session?])
            leaders-search (subscribe [::gql/query {:queries [(build-leaders-query @form-data nil)]}
                                         {:id (merge @form-data {:active-session @active-session
                                                                 :active-account-has-session? @active-account-has-session?})}])
            rounds-search (subscribe [::gql/query {:queries [(build-rounds-query)]}])
            rounds (into [{:value "overall" :label "All rounds"}] (map (fn [round]
                          (let [round-id (:round/id round)]
                            {:value round-id
                             :label (str "Round: " round-id)})) (-> @rounds-search :search-rounds :items)))]
        [app-layout
         [:main.pageSite
          {:id "leaderboard"}
          [:div.headerLeaderboard
           [:div.container
            [:h1.titlePage "Leaderboard"]
            [search-tools {:form-data form-data
                           :search-id :search-term
                           :select-options leaders-order}
              [:div.custom-select.selectForm.round
               [select {:form-data form-data
                        :id :round
                        :class "options"
                        :options rounds}]]]]]
          [:div.contentLeaderboard.container
           [:div.headerLeaderboards.d-none.d-md-flex
            [:div.cel-data
             [:span.titleCel.col-user "Artist Name"]]
            [:div.cel-score
             [:span.titleCel.col-amount "Amount Granted"]
             [:span.titleCel.col-matching "Matching Received"]
             [:span.titleCel.col-total "Total Received"]]]
           [leaderboard-entries form-data leaders-search]]]]))))
