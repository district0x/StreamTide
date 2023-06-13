(ns streamtide.ui.grants.page
  "Page showing a list of users whose grant has been approved"
  (:require
    [district.format :as format]
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
    [streamtide.ui.components.user :refer [user-photo avatar-placeholder]]))

(def page-size 6)

(def grants-order [{:key "decision-date/desc" :value "Newest"}
                   {:key "decision-date/asc" :value "Oldest"}
                   {:key "username/asc" :value "Username"}])

(defn build-grants-query [{:keys [:search-term :order-key]} after]
  (let [[order-by order-dir] ((juxt namespace name) (keyword order-key))]
    [:search-grants
     (cond-> {:first page-size
              :statuses [:grant.status/approved]}
             (not-empty search-term) (assoc :search-term search-term)
             after                   (assoc :after after)
             order-by                (assoc :order-by (keyword "grants.order-by" order-by))
             order-dir               (assoc :order-dir (keyword order-dir)))
     [:total-count
      :end-cursor
      :has-next-page
      [:items [:grant/status
               [:grant/user [:user/address
                             :user/name
                             :user/description
                             :user/photo
                             :user/bg-photo]]]]]]))


(defn grant-card [{:keys [:user/address :user/photo :user/bg-photo :user/name :user/description :star?]}]
  [:li.card {:key address}
   [nav-anchor {:route :route.profile/index :params {:address address}}
    [:div.thumb
     [:img {:src (or photo avatar-placeholder)}]]
    [:div.content
     (when bg-photo [user-photo (merge {:src bg-photo} (when star? {:class "star"}))])
     [:h3 name]
     [:p (format/truncate description 180)]]]])


(defn grant-tiles [form-data grants-search]
  (let [all-grants (->> @grants-search
                        (mapcat (fn [r] (-> r :search-grants :items))))
        loading? (:graphql/loading? (last @grants-search))
        has-more? (-> (last @grants-search) :search-grants :has-next-page)]
    (if (and (empty? all-grants)
             (not loading?))
      [no-items-found]
      [infinite-scroll {:class "contentGrants.container"
                        :elements-in-row 4
                        :element-height 568
                        :fire-tutorial-next-on-items? true
                        :loading? loading?
                        :has-more? has-more?
                        :loading-spinner-delegate (fn []
                                                    [:div.spinner-container [spinner/spin]])
                        :load-fn #(let [{:keys [:end-cursor]} (:search-grants (last @grants-search))]
                                    (dispatch [::graphql-events/query
                                               {:query {:queries [(build-grants-query @form-data end-cursor)]}
                                                :id @form-data}]))}
       (when-not (:graphql/loading? (first @grants-search))
          (doall
           (for [{:keys [:grant/user]} all-grants]
             ^{:key (:user/address user)} [grant-card user])))])))


(defmethod page :route.grants/index []
  (let [form-data (r/atom {:search-term ""
                           :order-key (:key (first grants-order))})]
    (fn []
      (let [grants-search (subscribe [::gql/query {:queries [(build-grants-query @form-data nil)]}
                                     {:id @form-data}])]
        [app-layout
         [:main.pageSite
          {:id "grants"}
          [:div.headerGrants
           [:div.container
            [:h1.titlePage "Grants"]
            [search-tools {:form-data form-data
                           :search-id :search-term
                           :select-options grants-order}]]]
          [grant-tiles form-data grants-search]]]))))
