(ns streamtide.ui.admin.rounds.page
  "Page to show and manage rounds.
  It shows a list of previous rounds and its details and also allow create new ones"
  (:require
    [district.ui.component.form.input :refer [text-input amount-input int-input pending-button]]
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.events :as graphql-events]
    [district.ui.graphql.subs :as gql]
    [district.ui.web3-tx-id.subs :as tx-id-subs]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]
    [streamtide.shared.utils :as shared-utils]
    [streamtide.ui.admin.rounds.events :as r-events]
    [streamtide.ui.components.admin-layout :refer [admin-layout]]
    [streamtide.ui.components.general :refer [nav-anchor no-items-found]]
    [streamtide.ui.components.infinite-scroll :refer [infinite-scroll]]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.components.warn-popup :as warn-popup]
    [streamtide.ui.subs :as st-subs]
    [streamtide.ui.utils :as ui-utils]))

(def page-size 6)

(defn build-rounds-query [after]
  [:search-rounds
   (cond-> {:first page-size
            :order-by :rounds.order-by/id
            :order-dir :desc}
           after (assoc :after after))
   [:total-count
    :end-cursor
    :has-next-page
    [:items [:round/id
             :round/start
             :round/duration
             :round/matching-pool
             :round/distributed]]]])

(defn round-entry [{:keys [:round/id :round/start :round/matching-pool :round/duration :round/distributed] :as round}]
  (let [nav (partial nav-anchor {:route :route.admin/round :params {:round id}})
        active? (shared-utils/active-round? round (shared-utils/now-secs))]
    [:div.contentRound
     (when active? {:class "active"})
     [:div.cel.name
      [:h4.d-lg-none "ID"]
      [nav [:h4 id]]]
     [:div.cel.start
      [:h4.d-lg-none "Start Date"]
      [nav [:span (ui-utils/format-graphql-time start)]]]
     [:div.cel.duration
      [:h4.d-lg-none "End Date"]
      [nav [:span (ui-utils/format-graphql-time (+ start duration))]]]
     [:div.cel.matching-pool
      [:h4.d-lg-none "Matching Pool"]
      [nav [:span (shared-utils/format-price matching-pool)]]]
     [:div.cel.distributed
      [:h4.d-lg-none "Distributed"]
      [nav [:span (shared-utils/format-price distributed)]]]]))


(defn round-entries [rounds-search]
  (let [all-rounds (->> @rounds-search
                       (mapcat (fn [r] (-> r :search-rounds :items)))
                        distinct
                        (sort-by #(:round/id %))
                        reverse)
        loading? (:graphql/loading? (last @rounds-search))
        has-more? (-> (last @rounds-search) :search-rounds :has-next-page)]
    (if (and (empty? all-rounds)
             (not loading?))
      [no-items-found]
      [infinite-scroll {:id "rounds"
                        :class "contentRounds"
                        :fire-tutorial-next-on-items? true
                        :loading? loading?
                        :has-more? has-more?
                        :element-height 86
                        :loading-spinner-delegate (fn []
                                                    [:div.spinner-container [spinner/spin]])
                        :load-fn #(let [{:keys [:end-cursor]} (:search-rounds (last @rounds-search))]
                                    (dispatch [::graphql-events/query
                                               {:query {:queries [(build-rounds-query end-cursor)]}
                                                :id :rounds}]))}
       (when-not (:graphql/loading? (first @rounds-search))
         (doall
           (for [{:keys [:round/id] :as round} all-rounds]
             ^{:key id} [round-entry round])))])))

(defn get-duration [{:keys [:days :hours]}]
  (* 3600 (+ (* 24 days) hours)))

(defn round-distributed? [round]
  (or (= (:round/matching-pool round) "0")
    (not= (:round/distributed round) "0")))

(defmethod page :route.admin/rounds []
  (let [form-data (r/atom {:days 10 :hours 0})
        tx-id (str "start-round_" (random-uuid))]
    (fn []
      (let [rounds-search (subscribe [::gql/query {:queries [(build-rounds-query nil)]}
                                      {:id :rounds
                                       :refetch-on [::r-events/round-started]}])
            last-active? (-> @rounds-search first :search-rounds :items first (shared-utils/active-round? (shared-utils/now-secs)))
            start-round-tx-pending? (subscribe [::tx-id-subs/tx-pending? {:streamtide/start-round tx-id}])
            start-round-tx-success? (subscribe [::tx-id-subs/tx-success? {:streamtide/start-round tx-id}])
            waiting-wallet? (subscribe [::st-subs/waiting-wallet? {:streamtide/start-round tx-id}])]
        [admin-layout
         [:div.headerRound
          [:span.titleCel "Start new Round"]
          [:div.form.formRounds
           [:div.field.duration
            [:div.fieldDescription "Duration"]
            [:div.inputContainer
             [:label.inputField
              [:span "Days"]
              [int-input {:id :days
                          :form-data form-data}]]
             [:label.inputField
              [:span "Hours"]
              [int-input {:id :hours
                          :form-data form-data}]]]]
           [:div.field.matchingPool
            [:div.fieldDescription "Matching Pool"]
            [:div.inputContainer
             [:label.inputField
              [:span "ETH"]
              [amount-input {:id :pool
                             :form-data form-data}]]]]
           [pending-button {:pending? (or @start-round-tx-pending? @waiting-wallet?)
                            :pending-text "Starting"
                            :disabled (or @start-round-tx-pending? @start-round-tx-success? last-active? @waiting-wallet?)
                            :class (str "btBasic btBasic-light btStartRound")
                            :on-click (fn [e]
                                        (.stopPropagation e)
                                        (dispatch
                                          [::warn-popup/show-popup
                                           {:check #(not (round-distributed? (-> @rounds-search first :search-rounds :items first)))
                                            :on-accept [::r-events/start-round {:send-tx/id tx-id
                                                                                :duration (get-duration @form-data)
                                                                                :matching-pool (:pool @form-data)}]}]))}
            (if @start-round-tx-success? "Started" "Start")]]]
          [:div.headerRound
           [:h3 "Previous Rounds"]]
          [:div.headerRounds.d-none.d-lg-flex
           [:div.cel.cel-id
            [:span.titleCel.col-id "Id"]]
           [:div.cel.cel-date
            [:span.titleCel.col-eth "Start Date"]]
           [:div.cel.cel-duration
            [:span.titleCel.col-eth "End Date"]]
           [:div.cel.cel-matching
            [:span.titleCel.col-eth "Matching Pool"]]
           [:div.cel.cel-distributed
            [:span.titleCel.col-eth "Distributed"]]]
         [round-entries rounds-search]
         [warn-popup/warn-popup {:content "The previous round has not been distributed yet. Creating a new round will no longer allow distribution of the previous round. Are you certain you want to proceed?"
                                 :button-label "Start New Round"}]]))))
