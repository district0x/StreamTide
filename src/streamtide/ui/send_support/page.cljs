(ns streamtide.ui.send-support.page
  "Page to make donations. Shows the content of card and allow triggering a TX to send donations"
  (:require
    [cljs-time.coerce :as tc]
    [clojure.string :as str]
    [district.ui.component.form.input :refer [text-input pending-button get-by-path]]
    [district.ui.component.page :refer [page]]
    [district.ui.conversion-rates.subs :as conversation-rates-subs]
    [district.ui.graphql.events :as graphql-events]
    [district.ui.graphql.subs :as gql]
    [district.ui.router.events :as router-events]
    [district.ui.web3-accounts.subs :as accounts-subs]
    [district.ui.web3-tx-id.subs :as tx-id-subs]
    [re-frame.core :as re-frame :refer [subscribe dispatch]]
    [reagent.core :as r]
    [reagent.ratom :refer [reaction]]
    [reagent.ratom :refer [reaction]]
    [streamtide.shared.utils :as shared-utils]
    [streamtide.ui.components.app-layout :refer [app-layout]]
    [streamtide.ui.components.general :refer [nav-anchor no-items-found support-seal]]
    [streamtide.ui.components.infinite-scroll :refer [infinite-scroll]]
    [streamtide.ui.components.spinner :as spinner]
    [streamtide.ui.components.user :refer [user-photo]]
    [streamtide.ui.events :as st-events]
    [streamtide.ui.send-support.events :as ss-events]
    [streamtide.ui.send-support.subs :as ss-subs]
    [streamtide.ui.subs :as st-subs]
    [streamtide.ui.utils :as ui-utils]))

(def page-size 6)

(defn build-user-info-query [{:keys [:user/address]}]
  [:user
   {:user/address address}
   [:user/address
    :user/name
    :user/tagline
    :user/photo
    :user/donations-type
    :user/min-donation
    [:user/donation-coin [:coin/address
                          :coin/symbol
                          :coin/type]]]])

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
             :donation/amount-usd
             [:donation/coin [:coin/symbol
                              :coin/decimals]]
             [:donation/receiver [:user/address
                                  :user/name
                                  :user/photo]]]]]])


(def default-min-donation "0.005")

(defn send-support-card [user-info form-data errors]
  (let [user-address (:user/address user-info)
        nav (partial nav-anchor {:route :route.profile/index :params {:address user-address}})
        coin (:user/donation-coin user-info)
        erc721? (ui-utils/erc721? coin)
        min-donation (shared-utils/from-wei (or (:user/min-donation user-info) "0"))
        eth->usd @(subscribe [::conversation-rates-subs/conversion-rate :ETH :USD])
        token->wei (when erc721? @(subscribe [::ss-subs/coin-conversion (:coin/address coin)]))
        amount (get-in @form-data [user-address :amount])
        usd-amount (when (and amount eth->usd)
                     (if erc721?
                       (when token->wei
                         (-> amount (* (shared-utils/from-wei token->wei) eth->usd) ui-utils/format-to-usd))
                       (-> amount (* eth->usd) ui-utils/format-to-usd)))]
    (when-not (get-in @form-data [user-address :amount])
      (swap! form-data assoc-in [user-address :amount]
             (if erc721? "1"
              (if (= "0" min-donation) default-min-donation min-donation))))
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
       [:span (:coin/symbol coin)]]]
     [:button.btClose
      {:on-click (fn []
                   (swap! form-data dissoc user-address)
                   (dispatch [::st-events/remove-from-cart {:user/address user-address}]))}]
     [:div.usd-price (when usd-amount (str "$" usd-amount))]]))

(defn donation-entry [{:keys [:donation/id :donation/receiver :donation/amount :donation/date :donation/coin :donation/amount-usd] :as donation}]
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
       [:span (shared-utils/format-price amount coin)
        (when amount-usd [:span.usd-price (str " ($" (ui-utils/format-to-usd amount-usd) ")")])]]]]))

(defn donations []
  (let [active-account (subscribe [::accounts-subs/active-account])]
    (fn []
      (let [donations-search (subscribe [::gql/query {:queries [(build-donations-query {:user/address @active-account} nil)]}
                                                               {:id :user-donations
                                                                :refetch-on [::ss-events/send-support-success]}])
            loading? (:graphql/loading? (last @donations-search))
            donations (->> @donations-search
                           (mapcat (fn [r] (-> r :search-donations :items)))
                           distinct
                           (sort-by #(tc/to-long (:donation/date %)))
                           reverse)
            has-more? (-> (last @donations-search) :search-donations :has-next-page)]
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
                             :load-fn #(let [end-cursor (:end-cursor (:search-donations (last @donations-search)))]
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
        form-data (r/atom
                    (reduce (fn [aggr [address _]]
                              (merge aggr {address nil})) {} @cart))
        queries (map-indexed (fn [idx [address _]] {:query/data (build-user-info-query {:user/address address})
                                                    :query/alias (keyword (str "a-" idx))}) @cart)
        user-info-query (when-not (empty? @cart) (subscribe [::gql/query {:queries queries}]))
        tx-id (str "donate_" (random-uuid))
        active-account (subscribe [::accounts-subs/active-account])]
    (fn []
      (let [loading? (and (some? user-info-query) (:graphql/loading? @user-info-query))
            users-map (when (and (some? user-info-query) (not loading?))
                        (into {} (keep (fn [[_ v]]
                                         (when-let [addr (:user/address v)]
                                           [addr v])))
                              @user-info-query))
            errors (reaction {:local
                              (when-not loading? (reduce (fn [aggr [addr {:keys [:amount]}]]
                                                           (let [erc721? (ui-utils/erc721? (-> users-map (get addr) :user/donation-coin))]
                                                             (if (and amount (or (and erc721? (not (re-matches #"\d+" amount)))
                                                                                 (not (re-matches #"^\d+(\.\d{0,18})?$" amount))
                                                                                 (re-matches #"^0+\.?0*$" amount)))
                                                               (assoc-in aggr [addr :amount] "Amount not valid")
                                                               aggr)))
                                                         {} @form-data))})
            donate-tx-pending? (subscribe [::tx-id-subs/tx-pending? {:streamtide/donate tx-id}])
            donate-tx-success? (subscribe [::tx-id-subs/tx-success? {:streamtide/donate tx-id}])
            waiting-wallet? (subscribe [::st-subs/waiting-wallet? {:streamtide/donate tx-id}])]
        [app-layout
         [:main.pageSite
          {:id "send-support"}
          [:div.container
            [:div.headerSendSupport
              [:h1.titlePage "Simping"]]
           (if loading?
             [spinner/spin]
             [:div.cart
              (if (empty? @form-data)
                [no-items-found {:message "Your cart is empty 😢"}]
                [:div.contentSendSupport
                 [support-seal]
                 (doall
                   (for [[alias user-info] @user-info-query]
                     (when (str/starts-with? (str alias) ":a-")
                       ^{:key alias} [send-support-card user-info form-data errors])))])
                 [:div.buttons
                  [pending-button {:pending? (or @donate-tx-pending? @waiting-wallet?)
                                   :pending-text "Simping in Progress 💸"
                                   :disabled (or @donate-tx-pending? @donate-tx-success? @waiting-wallet?
                                                 (empty? @form-data)
                                                 (some #(or (zero? %) (nil? %)) (map :amount (vals @form-data))))
                                   :class (str "btBasic btBasic-light btCheckout" (when @donate-tx-success? " checkedOut"))
                                   :on-click (fn [e]
                                               (.stopPropagation e)
                                               (dispatch [::ss-events/send-support {:donations @form-data
                                                                                    :users-info users-map
                                                                                    :send-tx/id tx-id}]))}
                   (if @donate-tx-success? "Thanks champ! 😉" "SIMP TODAY! 🤑")]
                  [:button.btBasic.btBasic-light.btKeep
                   {:on-click #(dispatch [::router-events/navigate :route.grants/index])}
                   "KEEP BROWSING"]]])]
          (when @active-account
            [:div.container
             [:div.headerPastDonations
              [:h2 "Past Donations"]]
             [:div.headerDonations.d-none.d-lg-flex
              [:div.cel-data
               [:span.titleCel.col-user "Artist Name"]]
              [:div.cel-score
               [:span.titleCel.col-date "Date"]
               [:span.titleCel.col-amount "Amount"]]]
             [donations]])]]))))
