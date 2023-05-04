(ns streamtide.ui.leaderboard.page
  "Page showing a list of top donations and matchings"
  (:require
    [district.ui.component.form.input :refer [select-input]]
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
    [streamtide.ui.components.user :refer [user-photo]]))

(def page-size 6)

(def leaders-order [{:key "total-amount/desc" :value "Total Higher"}
                    {:key "total-amount/asc" :value "Total Lower"}
                    {:key "donation-amount/desc" :value "Granted Higher"}
                    {:key "donation-amount/asc" :value "Granted Lower"}
                    {:key "matching-amount/desc" :value "Matching Higher"}
                    {:key "matching-amount/asc" :value "Matching Lower"}
                    {:key "username/asc" :value "Artist Name"}])

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
               :leader/matching-amount
               :leader/total-amount
               [:leader/receiver [:user/address
                                  :user/name
                                  :user/photo]]]]]]))

(defn build-rounds-query []
  [:search-rounds
   {:first 100}
   [[:items [:round/id]]]])

(defn leaderboard-entry [{:keys [:leader/receiver :leader/donation-amount :leader/matching-amount :leader/total-amount]}]
  (let [nav (partial nav-anchor {:route :route.profile/index :params {:address (:user/address receiver)}})]
    [:div.leaderboard
     [nav [user-photo {:class "lb" :src (:user/photo receiver)}]]
     [:div.data
      [nav [:h3 (:user/name receiver)]]]
     [:ul.score
      [:li
       ;; TODO format amount (wei->eth)
       [:span donation-amount]]
      [:li
       [:span matching-amount]]
      [:li
       [:span total-amount]]]]))

(defn leaderboard-entries [form-data leaders-search]
  (let [_ (print @leaders-search)
        all-leaders (->> @leaders-search
                           (mapcat (fn [r] (-> r :search-leaders :items))))
        loading? (:graphql/loading? (last @leaders-search))
        has-more? (-> (last @leaders-search) :search-ledaerss :has-next-page)]
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
                                                :id @form-data}]))}
       (when-not (:graphql/loading? (first @leaders-search))
         (doall
           (for [leader all-leaders]
             ^{:key (-> leader :leader/receiver :user/address)} [leaderboard-entry leader])))])))

(defmethod page :route.leaderboard/index []
  (let [form-data (r/atom {:search-term ""
                           :order-key (:key (first leaders-order))
                           :round "overall"})]
    (fn []
      (let [leaders-search (subscribe [::gql/query {:queries [(build-leaders-query @form-data nil)]}
                                         {:id @form-data}])
            rounds-search (subscribe [::gql/query {:queries [(build-rounds-query)]}])
            rounds (into [{:key "overall" :value "All rounds"}] (map (fn [round]
                          (let [round-id (:round/id round)]
                            {:key round-id
                             :value (str "Round: " round-id)})) (-> @rounds-search :search-rounds :items)))]
        [app-layout
         [:main.pageSite
          {:id "leaderboard"}
          [:div.headerLeaderboard
           [:div.container
            [:h1.titlePage "Leaderboard"]
            [search-tools {:form-data form-data
                           :search-id :search-term
                           :select-options leaders-order}
              [:div.custom-select.selectForm.inputField.round
               [select-input {:form-data form-data
                              :id :round
                              :group-class :options
                              :value js/undefined
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
