(ns streamtide.ui.send-support.page
  "Page to make donations. Shows the content of card and allow triggering a TX to send donations"
  (:require
    [district.ui.component.form.input :refer [amount-input pending-button]]
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.subs :as gql]
    [district.ui.router.events :as router-events]
    [district.ui.web3-tx-id.subs :as tx-id-subs]
    [re-frame.core :as re-frame :refer [subscribe dispatch]]
    [reagent.core :as r]
    [streamtide.ui.components.app-layout :refer [app-layout]]
    [streamtide.ui.components.general :refer [nav-anchor no-items-found support-seal]]
    [streamtide.ui.components.user :refer [user-photo]]
    [streamtide.ui.events :as st-events]
    [streamtide.ui.send-support.events :as ss-events]
    [streamtide.ui.subs :as st-subs]
    [streamtide.ui.utils :as ui-utils]))

(defn build-user-info-query [{:keys [:user/address]}]
  [:user
   {:user/address address}
   [:user/name
    :user/tagline
    :user/photo]])

(defn send-support-card [user-address form-data]
  (let [user-info-query (subscribe [::gql/query {:queries [(build-user-info-query {:user/address user-address})]}])
        loading? (or (nil? user-info-query) (:graphql/loading? (last @user-info-query)))]
    (fn []
      (let [user-info (:user @user-info-query)
            nav (partial nav-anchor {:route :route.profile/index :params {:address user-address}})]
        [:div.cardSendSupport
         [nav [user-photo {:src (:user/photo user-info)}]]
         [:div.content
          [nav [:h3 (ui-utils/user-or-address (:user/name user-info) user-address)]]
          [:p.d-none.d-lg-block (:user/tagline user-info)]]
         [:div.field.field-amount
          [:span.titleField "Amount"]
          [amount-input {:id [user-address :amount]
                         :form-data form-data
                         :class "inputField"}]]
         [:div.field.field-currency
          [:span.titleField "Currency"]
          [:div.custom-select.selectForm.inputField.simple
           [:select
            [:option {:value "eth"} "ETH"]]]]
         [:button.btClose
          {:on-click (fn []
                       (swap! form-data dissoc user-address)
                       (dispatch [::st-events/remove-from-cart {:user/address user-address}]))}]]))))

(defmethod page :route.send-support/index []
  (let [cart (subscribe [::st-subs/cart])
        form-data (r/atom (into {} (map (fn [[address _]] [address {:amount 0.01}]) @cart)))
        tx-id (str "donate_" (random-uuid))]
    (fn []
      (let [donate-tx-pending? (subscribe [::tx-id-subs/tx-pending? {:streamtide/donate tx-id}])
            donate-tx-success? (subscribe [::tx-id-subs/tx-success? {:streamtide/donate tx-id}])]
        [app-layout
         [:main.pageSite
          {:id "send-support"}
          [:div.container
            [:div.headerSendSupport
              [:h1.titlePage "Send Support"]]
            [:div
             (if (empty? @cart)
               [no-items-found]
               [:div.contentSendSupport
                [support-seal]
                (doall
                  (for [[address _] @cart]
                    ^{:key address} [send-support-card address form-data]))])
                [:div.buttons
                 [pending-button {:pending? @donate-tx-pending?
                                  :pending-text "CHECKING OUT"
                                  :disabled (or @donate-tx-pending? @donate-tx-success?
                                                (empty? @form-data)
                                                (some #(or (zero? %) (nil? %)) (map :amount (vals @form-data))))
                                  :class (str "btBasic btBasic-light btCheckout" (when-not @donate-tx-success? " checkedOut"))
                                  :on-click (fn [e]
                                              (.stopPropagation e)
                                              (dispatch [::ss-events/send-support {:donations @form-data
                                                                                   :send-tx/id tx-id}]))}
                  (if @donate-tx-success? "CHECKED OUT" "CHECKOUT")]
                 [:button.btBasic.btBasic-light.btKeep
                  {:on-click #(dispatch [::router-events/navigate :route.grants/index])}
                  "KEEP BROWSING"]]]]]]))))
