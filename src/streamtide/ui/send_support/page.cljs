(ns streamtide.ui.send-support.page
  "Page to make donations. Shows the content of card and allow triggering a TX to send donations"
  (:require
    [cljs-time.coerce :as tc]
    [district.ui.component.form.input :refer [text-input pending-button get-by-path]]
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.events :as graphql-events]
    [district.ui.graphql.subs :as gql]
    [district.ui.router.events :as router-events]
    [district.ui.web3-accounts.subs :as accounts-subs]
    [district.ui.web3-tx-id.subs :as tx-id-subs]
    [re-frame.core :as re-frame :refer [subscribe dispatch]]
    [reagent.core :as r]
    [reagent.ratom :refer [reaction]]
    [streamtide.ui.components.app-layout :refer [app-layout]]
    [streamtide.ui.components.general :refer [nav-anchor no-items-found support-seal]]
    [streamtide.ui.components.infinite-scroll :refer [infinite-scroll]]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.components.user :refer [user-photo]]
    [streamtide.ui.events :as st-events]
    [streamtide.ui.send-support.events :as ss-events]
    [streamtide.ui.subs :as st-subs]
    [streamtide.ui.utils :as ui-utils]))

(def page-size 6)

(defn build-user-info-query [{:keys [:user/address]}]
  [:user
   {:user/address address}
   [:user/name
    :user/tagline
    :user/photo
    :user/min-donation]])

(defn build-donations-query [{:keys [:user/address]} after]
  [:search-donations
   (cond-> {:first page-size
            :sender address
            :order-by :donations.order-by/date
            :order-dir :desc}
           after (assoc :after after))
   [:total-count
    :end-cursor
    :has-next-page
    [:items [:donation/id
             :donation/date
             :donation/amount
             :donation/coin
             [:donation/receiver [:user/address
                                  :user/name
                                  :user/photo]]]]]])


(def default-min-donation "0.005")

(defn send-support-card [user-address form-data errors]
  (let [user-info-query (subscribe [::gql/query {:queries [(build-user-info-query {:user/address user-address})]}])]
    (fn []
      (let [loading? (or (nil? user-info-query) (:graphql/loading? @user-info-query))]
        (if loading?
          [spinner/spin]
          (let [user-info (:user @user-info-query)
                nav (partial nav-anchor {:route :route.profile/index :params {:address user-address}})
                min-donation (ui-utils/from-wei (or (:user/min-donation user-info) "0"))]
            (when-not (get-in @form-data [user-address :amount])
              (swap! form-data assoc-in [user-address :amount]
                     (if (= "0" min-donation) default-min-donation min-donation)))
            [:div.cardSendSupport
             [nav [user-photo {:src (:user/photo user-info)}]]
             [:div.content
              [nav [:h3 (ui-utils/user-or-address (:user/name user-info) user-address)]]
              [:p.d-none.d-lg-block (:user/tagline user-info)]]
             [:div.field.field-amount
              [:span.titleField "Amount"]
              [text-input {:id [user-address :amount]
                           :form-data form-data
                           :class "inputField"
                           :errors errors}]
              (when (and (nil? (get-in @errors [:local user-address :amount]))
                         (< (js/parseFloat (get-in @form-data [user-address :amount]))
                            (js/parseFloat min-donation)))
                [:span.warning "Min amount not reached. This donation will not unlock hidden content"])]
             [:div.field.field-currency
              [:span.titleField "Currency"]
              [:div.inputField.simple.disabled
               [:span "ETH"]]]
             [:button.btClose
              {:on-click (fn []
                           (swap! form-data dissoc user-address)
                           (dispatch [::st-events/remove-from-cart {:user/address user-address}]))}]]))))))

(defn donation-entry [{:keys [:donation/id :donation/receiver :donation/amount :donation/date] :as donation}]
  (let [receiver-address (:user/address receiver)
        nav (partial nav-anchor {:route :route.profile/index :params {:address receiver-address}})]
    [:div.donation
     [nav [user-photo {:src (:user/photo receiver)}]]
     [:div.data
      [nav [:h3 (ui-utils/user-or-address (:user/name receiver) receiver-address)]]]
     [:ul.score
      [:li
       [:h4.d-lg-none "Date"]
       [:span (ui-utils/format-graphql-time date)]]
      [:li
       [:h4.d-lg-none "Amount"]
       [:span (ui-utils/format-price amount)]]]]))

(defn donations []
  (let [active-account (subscribe [::accounts-subs/active-account])]
    (fn []
      (let [donations-search (when @active-account (subscribe [::gql/query {:queries [(build-donations-query {:user/address @active-account} nil)]}
                                                               {:id :user-donations
                                                                :refetch-on [::ss-events/send-support-success]}]))
            loading? (or (nil? donations-search) (:graphql/loading? (last @donations-search)))
            donations (when @active-account (->> @donations-search
                                                 (mapcat (fn [r] (-> r :search-donations :items)))
                                                 distinct
                                                 (sort-by #(tc/to-long (:donation/date %)))
                                                 reverse))
            has-more? (when @active-account (-> (last @donations-search) :search-donations :has-next-page))]
        [:div.containerDonations
         (if (and (empty? donations)
                  (not loading?))
           [no-items-found]
           [infinite-scroll {:class "yourDonations"
                             :fire-tutorial-next-on-items? true
                             :element-height 86
                             :loading? loading?
                             :has-more? has-more?
                             :loading-spinner-delegate (fn []
                                                         [:div.spinner-container [spinner/spin]])
                             :load-fn #(let [end-cursor (:end-cursor (:search-contents (last @donations-search)))]
                                         (dispatch [::graphql-events/query
                                                    {:query {:queries [(build-donations-query {:user/address @active-account} end-cursor)]}
                                                     :id :user-donations
                                                     :refetch-on [::ss-events/send-support-success]}]))}
            (when @active-account
              (doall
                (for [{:keys [:donation/id] :as donation} donations]
                  ^{:key id}
                  [donation-entry donation])))])]))))

(defmethod page :route.send-support/index []
  (let [cart (subscribe [::st-subs/cart])
        form-data (r/atom {})
        errors (reaction {:local
                          (reduce (fn [aggr [addr {:keys [:amount]}]]
                                    (if (and amount (or (not (re-matches #"^\d+(\.\d{0,18})?$" amount))
                                                        (re-matches #"^0+\.?0*$" amount)))
                                      (assoc-in aggr [addr :amount] "Amount not valid")
                                      aggr))
                                  {} @form-data)})
        tx-id (str "donate_" (random-uuid))]
    (fn []
      (let [donate-tx-pending? (subscribe [::tx-id-subs/tx-pending? {:streamtide/donate tx-id}])
            donate-tx-success? (subscribe [::tx-id-subs/tx-success? {:streamtide/donate tx-id}])]
        [app-layout
         [:main.pageSite
          {:id "send-support"}
          [:div.container
            [:div.headerSendSupport
              [:h1.titlePage "Simping"]]
            [:div.cart
             (if (empty? @cart)
               [no-items-found {:message "Your cart is empty 😢"}]
               [:div.contentSendSupport
                [support-seal]
                (doall
                  (for [[address _] @cart]
                    ^{:key address} [send-support-card address form-data errors]))])
                [:div.buttons
                 [pending-button {:pending? @donate-tx-pending?
                                  :pending-text "Simping in Progress 💸"
                                  :disabled (or @donate-tx-pending? @donate-tx-success?
                                                (empty? @form-data)
                                                (some #(or (zero? %) (nil? %)) (map :amount (vals @form-data))))
                                  :class (str "btBasic btBasic-light btCheckout" (when-not @donate-tx-success? " checkedOut"))
                                  :on-click (fn [e]
                                              (.stopPropagation e)
                                              (dispatch [::ss-events/send-support {:donations @form-data
                                                                                   :send-tx/id tx-id}]))}
                  (if @donate-tx-success? "Thanks champ! 😉" "SIMP TODAY! 🤑")]
                 [:button.btBasic.btBasic-light.btKeep
                  {:on-click #(dispatch [::router-events/navigate :route.grants/index])}
                  "KEEP BROWSING"]]]]
          [:div.container
           [:div.headerPastDonations
            [:h2 "Past Donations"]]
           [:div.headerDonations.d-none.d-lg-flex
            [:div.cel-data
             [:span.titleCel.col-user "Artist Name"]]
            [:div.cel-score
             [:span.titleCel.col-date "Date"]
             [:span.titleCel.col-amount "Amount"]]]
           [donations]]]]))))
