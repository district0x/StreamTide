(ns streamtide.ui.grants.page
  "Page showing a list of users whose grant has been approved"
  (:require
    [district.format :as format]
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.events :as graphql-events]
    [district.ui.graphql.subs :as gql]
    [district.ui.window-size.subs :as w-size-subs]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]
    [streamtide.ui.components.app-layout :refer [app-layout]]
    [streamtide.ui.components.general :refer [nav-anchor no-items-found]]
    [streamtide.ui.components.infinite-scroll :refer [infinite-scroll]]
    [streamtide.ui.components.search :refer [search-tools]]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.components.user :refer [user-photo avatar-placeholder]]
    [streamtide.ui.subs :as st-subs]))

(def page-size 6)

(def grants-order [{:value "decision-date/desc" :label "Newest"}
                   {:value "decision-date/asc" :label "Oldest"}
                   {:value "username/asc" :label "Username"}])

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
                             :user/bg-photo
                             :user/unlocked]]]]]]))


(defn grant-card [{:keys [:user/address :user/bg-photo :user/photo :user/name :user/description :user/unlocked]}]
  [:li.card {:key address}
   [nav-anchor {:route :route.profile/index :params {:address address}}
    [:div.thumb
     {:class (when unlocked "star")}
     [:img {:src (or bg-photo avatar-placeholder)}]] 
    [:div.content
     (when photo [user-photo {:src photo}])
     [:h3 name]
     [:p (format/truncate description 180)]]]])



(defn grant-tiles [form-data grants-search]
  (let [all-grants (->> @grants-search
                        (mapcat (fn [r] (-> r :search-grants :items))))
        loading? (:graphql/loading? (last @grants-search))
        has-more? (-> (last @grants-search) :search-grants :has-next-page)
        active-session (subscribe [::st-subs/active-session])
        active-account-has-session? (subscribe [::st-subs/active-account-has-session?])]
    (if (and (empty? all-grants)
             (not loading?))
      [no-items-found]
      [infinite-scroll {:class "contentGrants container"
                        :elements-in-row (min 4 (+ 2 @(subscribe [::w-size-subs/size])))
                        :element-height 568
                        :fire-tutorial-next-on-items? true
                        :loading? loading?
                        :has-more? has-more?
                        :loading-spinner-delegate (fn []
                                                    [:div.spinner-container [spinner/spin]])
                        :load-fn #(let [{:keys [:end-cursor]} (:search-grants (last @grants-search))]
                                    (dispatch [::graphql-events/query
                                               {:query {:queries [(build-grants-query @form-data end-cursor)]}
                                                :id (merge @form-data {:active-session @active-session
                                                                       :active-account-has-session? @active-account-has-session?})}]))}
       (when-not (:graphql/loading? (first @grants-search))
          (doall
           (for [{:keys [:grant/user]} all-grants]
             ^{:key (:user/address user)} [grant-card user])))])))


(defmethod page :route.grants/index []
  (let [form-data (r/atom {:search-term ""
                           :order-key (:value (first grants-order))})]
    (fn []
      (let [active-session (subscribe [::st-subs/active-session])
            active-account-has-session? (subscribe [::st-subs/active-account-has-session?])
            grants-search (subscribe [::gql/query {:queries [(build-grants-query @form-data nil)]}
                                     {:id (merge @form-data {:active-session @active-session
                                                             :active-account-has-session? @active-account-has-session?})}])]
        [app-layout
         [:main.pageSite
          {:id "grants"}
          [:div.headerGrants
           [:div.container
            [:h1.titlePage "Creators"]
            [search-tools {:form-data form-data
                           :search-id :search-term
                           :select-options grants-order}]]]
          [grant-tiles form-data grants-search]]]))))
