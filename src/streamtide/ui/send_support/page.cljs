(ns streamtide.ui.send-support.page
  "Page to make donations. Shows the content of card and allow triggering a TX to send donations"
  (:require
    [district.ui.component.form.input :refer [amount-input]]
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.subs :as gql]
    [district.ui.router.events :as router-events]
    [re-frame.core :as re-frame :refer [subscribe dispatch]]
    [reagent.core :as r]
    [streamtide.ui.components.app-layout :refer [app-layout]]
    [streamtide.ui.components.general :refer [no-items-found support-seal]]
    [streamtide.ui.components.user :refer [user-photo]]
    [streamtide.ui.events :as st-events]
    [streamtide.ui.send-support.events :as ss-events]
    [streamtide.ui.subs :as st-subs]))

;; TODO maybe move this to some common ns
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
      (let [user-info (:user @user-info-query)]
        [:div.cardSendSupport
         [user-photo {:src (:user/photo user-info)}]
         [:div.content
          [:h3 (:user/name user-info)]
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
          {:on-click #(dispatch [::st-events/remove-from-cart {:user/address user-address}])}]]))))

(defmethod page :route.send-support/index []
  (let [cart (subscribe [::st-subs/cart])
        form-data (r/atom (into {} (map (fn [[address _]] [address {:amount 0.01}]) @cart)))]
    (fn []
      [app-layout
       [:main.pageSite
        {:id "send-support"}
        [:div.container
          [:div.headerSendSupport
            [:h1.titlePage "Send Support"]]
         (if (empty? @cart)
           [no-items-found]
           [:div
             [:div.contentSendSupport
              [support-seal]
              (doall
                (for [[address _] @cart]
                  ^{:key address} [send-support-card address form-data]))]
              [:div.buttons
               [:button.btBasic.btBasic-light.btCheckout
                ;; TODO make transaction
                {:on-click #(dispatch [::ss-events/send-support {:form-data @form-data}]) }
                "CHECKOUT"]
               [:button.btBasic.btBasic-light.btKeep
                {:on-click #(dispatch [::router-events/navigate :route.grants/index])}
                "KEEP BROWSING"]]])]]])))
