(ns streamtide.ui.admin.black-listing.page
  "Page to show and manage blacklisted users.
  It shows a list of all users where and admin can blacklist or whitelist them"
  (:require
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
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.utils :as ui-utils]))

(def page-size 6)

(defn build-users-query [{:keys [:search-name :search-address]} after]
  (let [
        ;[order-by order-dir] ((juxt namespace name) (keyword order-key))
        ]
    [:search-users
     (cond-> {:first page-size}
             (not-empty search-name) (assoc :user/name search-name)
             (not-empty search-address) (assoc :user/address search-address)
             after                   (assoc :after after)
             ;order-by                (assoc :order-by (keyword "users.order-by" order-by))
             ;order-dir               (assoc :order-dir (keyword order-dir))
             )
     [:total-count
      :end-cursor
      :has-next-page
      [:items [:user/address
               :user/name
               :user/blacklisted]]]]))

(defn blacklist-entry [{:keys [:user/name :user/address :user/blacklisted]}]
  (fn []
    (let [tx-id (str "blacklist-" (random-uuid) address)]
      (let [loading? (subscribe [::bl-subs/blacklisting? address])
            blacklist-tx-pending? (subscribe [::tx-id-subs/tx-pending? {:streamtide/blacklist tx-id}])
            blacklist-tx-success? (subscribe [::tx-id-subs/tx-success? {:streamtide/blacklist tx-id}])
            blacklist-button (fn [{:keys [:text :pending :completed :class]} blacklisted?]
                               [pending-button {:pending? @blacklist-tx-pending?
                                                :pending-text pending
                                                :disabled (or @blacklist-tx-pending? @blacklist-tx-success?)
                                                :class (str "btBasic btBasic-light " class)
                                                :on-click (fn [e]
                                                            (.stopPropagation e)
                                                            (dispatch [::bl-events/blacklist {:user/address address
                                                                                              :user/blacklisted? blacklisted?
                                                                                              :send-tx/id tx-id}]))}
                              (if @blacklist-tx-success? completed text)])
            nav (partial nav-anchor {:route :route.profile/index :params {:address address}})]
        [:div.contentBlackList
         [:div.cel.name
          [nav [:h3 (ui-utils/truncate-text name 28)]]]
         [:div.cel.eth
          [nav [:span address]]]
         [:div.cel.button
          (when @loading? {:class "loading"})
          (if blacklisted
            [blacklist-button {:text "ALLOW" :pending "ALLOWING" :completed "ALLOWED" :class "btAllow"} false]
            [blacklist-button {:text "BLACKLIST" :pending "BLACKLISTING" :completed "BLACKLISTED" :class "btBlackList"} true])]]))))


(defn blacklist-list [form-data users-search]
  (let [all-users (->> @users-search
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
                                                :id @form-data}]))}
       (when-not (:graphql/loading? (first @users-search))
         (doall
           (for [{:keys [:user/address] :as user} all-users]
             ^{:key address} [blacklist-entry user])))])))


(defmethod page :route.admin/black-listing []
  (let [form-data (r/atom {:search-name ""
                           :search-address ""})
        search-input (r/atom {})]
    (fn []
      (let [users-search (subscribe [::gql/query {:queries [(build-users-query @search-input nil)]}
                                      {:id @search-input}])]
        [admin-layout
         [:div.headerBlackListing
          [:div.contentHeader.d-none.d-lg-flex
           [:div.cel.cel-data
            [:span.titleCel.col-user "Name"]]
           [:div.cel.cel-eth
            [:span.titleCel.col-eth "ETH Address"]]]
          [:div.form.formSearchList
           [:label.cel.cel-data
            [:h4.d-lg-none "Name"]
            [text-input {:id :search-name
                         :class "inputField"
                         :form-data form-data}]]
           [:label.cel.cel-eth
            [:h4.d-lg-none "ETH Address"]
            [text-input {:id :search-address
                         :class "inputField"
                         :form-data form-data}]]
           [:input.btBasic.btBasic-light {:type "submit" :on-click #(swap! search-input merge @form-data)
                                          :value "SEARCH"}]]]
         [blacklist-list search-input users-search]]))))
