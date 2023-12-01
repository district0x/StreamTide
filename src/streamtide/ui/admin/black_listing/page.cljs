(ns streamtide.ui.admin.black-listing.page
  "Page to show and manage blacklisted users.
  It shows a list of all users where and admin can blacklist or whitelist them"
  (:require
    [district.graphql-utils :as gql-utils]
    [district.ui.component.form.input :refer [text-input pending-button]]
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.events :as graphql-events]
    [district.ui.graphql.subs :as gql]
    [district.ui.web3-tx-id.subs :as tx-id-subs]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]
    [streamtide.ui.admin.black-listing.events :as bl-events]
    [streamtide.ui.admin.black-listing.subs :as bl-subs]
    [streamtide.ui.components.admin-layout :refer [admin-layout]]
    [streamtide.ui.components.general :refer [nav-anchor no-items-found]]
    [streamtide.ui.components.infinite-scroll :refer [infinite-scroll]]
    [streamtide.ui.components.search :refer [search-tools]]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.subs :as st-subs]
    [streamtide.ui.utils :as ui-utils]))

(def page-size 6)

(def users-order [{:value "creation-date/desc" :label "Newest"}
                  {:value "creation-date/asc" :label "Oldest"}
                  {:value "last-seen/desc" :label "Last Seen ↓"}
                  {:value "last-seen/asc" :label "Last Seen ↑"}
                  {:value "username/asc" :label "Username"}])

(defn build-users-query [{:keys [:search-term :order-key]} after]
  (let [[order-by order-dir] ((juxt namespace name) (keyword order-key))]
    [:search-users
     (cond-> {:first page-size}
             (not-empty search-term) (assoc :search-term search-term)
             after                   (assoc :after after)
             order-by                (assoc :order-by (keyword "users.order-by" order-by))
             order-dir               (assoc :order-dir (keyword order-dir))
             )
     [:total-count
      :end-cursor
      :has-next-page
      [:items [:user/address
               :user/name
               :user/blacklisted
               :user/creation-date
               :user/last-seen
               [:user/grant [:grant/status]]]]]]))

(defn blacklist-entry [{:keys [:user/name :user/address :user/blacklisted :user/creation-date :user/last-seen] :as user}]
  (fn []
    (let [tx-id (str "blacklist-" (random-uuid) address)]
      (let [{:keys [:grant/status]} (:user/grant user)
            loading? (subscribe [::bl-subs/blacklisting? address])
            blacklist-tx-pending? (subscribe [::tx-id-subs/tx-pending? {:streamtide/blacklist tx-id}])
            blacklist-tx-success? (subscribe [::tx-id-subs/tx-success? {:streamtide/blacklist tx-id}])
            waiting-wallet? (subscribe [::st-subs/waiting-wallet? {:streamtide/blacklist tx-id}])
            blacklist-button (fn [{:keys [:text :pending :completed :class]} blacklisted?]
                               [pending-button {:pending? (or @blacklist-tx-pending? @waiting-wallet?)
                                                :pending-text pending
                                                :disabled (or @blacklist-tx-pending? @blacklist-tx-success? @waiting-wallet?)
                                                :class (str "btBasic btBasic-light " class)
                                                :on-click (fn [e]
                                                            (.stopPropagation e)
                                                            (dispatch [::bl-events/blacklist {:user/address address
                                                                                              :user/blacklisted? blacklisted?
                                                                                              :send-tx/id tx-id}]))}
                              (if @blacklist-tx-success? completed text)])
            nav (partial nav-anchor {:route :route.profile/index :params {:address address}})
            status (-> status gql-utils/gql-name->kw cljs.core/name)]
        [:div.contentBlackList
         [:div.cel.name
          [nav [:h3 (ui-utils/truncate-text name 28)]]]
         [:div.cel.eth
          [nav [:span (ui-utils/truncate-text address)]]]
         [:div.cel.grant-status
          [:h4.d-lg-none "Grant Status"]
          [:span status]]
         [:div.cel.creation-date
          [:h4.d-lg-none "Creation Date / Last Seen"]
          [:span.creation (ui-utils/format-graphql-time creation-date)]
          [:span.last-seen (if last-seen (ui-utils/format-graphql-time last-seen) "N/A")]]
         [:div.cel.button
          (when @loading? {:class "loading"})
          (if blacklisted
            [blacklist-button {:text "ALLOW" :pending "ALLOWING" :completed "ALLOWED" :class "btAllow"} false]
            [blacklist-button {:text "BLACKLIST" :pending "BLACKLISTING" :completed "BLACKLISTED" :class "btBlackList"} true])]]))))


(defn blacklist-list [form-data users-search]
  (let [active-session (subscribe [::st-subs/active-session])
        active-account-has-session? (subscribe [::st-subs/active-account-has-session?])
        all-users (->> @users-search
                        (mapcat (fn [r] (-> r :search-users :items))))
        loading? (:graphql/loading? (last @users-search))
        has-more? (-> (last @users-search) :search-users :has-next-page)]
    (if (and (empty? all-users)
             (not loading?))
      [no-items-found]
      [infinite-scroll {:id "blackListing"
                        :class "contentBlackListing"
                        :fire-tutorial-next-on-items? true
                        :loading? loading?
                        :has-more? has-more?
                        :element-height 86
                        :loading-spinner-delegate (fn []
                                                    [:div.spinner-container [spinner/spin]])
                        :load-fn #(let [{:keys [:end-cursor]} (:search-users (last @users-search))]
                                    (dispatch [::graphql-events/query
                                               {:query {:queries [(build-users-query @form-data end-cursor)]}
                                                :id (merge @form-data {:active-session @active-session
                                                                       :active-account-has-session? @active-account-has-session?})}]))}
       (when-not (:graphql/loading? (first @users-search))
         (doall
           (for [{:keys [:user/address] :as user} all-users]
             ^{:key address} [blacklist-entry user])))])))


(defmethod page :route.admin/black-listing []
  (let [form-data (r/atom {:search-term ""
                           :order-key (:value (first users-order))})]
    (fn []
      (let [active-session (subscribe [::st-subs/active-session])
            active-account-has-session? (subscribe [::st-subs/active-account-has-session?])
            users-search (subscribe [::gql/query {:queries [(build-users-query @form-data nil)]}
                                      {:id (merge @form-data {:active-session @active-session
                                                              :active-account-has-session? @active-account-has-session?})}])]
        [admin-layout
         [:div.headerBlackListing
          [search-tools {:form-data form-data
                         :search-id :search-term
                         :select-options users-order}]
          [:div.contentHeader.d-none.d-lg-flex
           [:div.cel.cel-data
            [:span.titleCel.col-user "Name"]]
           [:div.cel.cel-eth
            [:span.titleCel.col-eth "ETH Address"]]
           [:div.cel.cel-grant-status
            [:span.titleCel.col-grant-status "Grant Status"]]
           [:div.cel.cel-creation-date
            [:span.titleCel.col-user-creation "Creation Date / Last Seen"]]]]
         [blacklist-list form-data users-search]]))))
