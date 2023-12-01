(ns streamtide.ui.components.general
  (:require
    [clojure.string :as str]
    [district.ui.router.events :as router-events]
    [district.ui.web3-accounts.subs :as accounts-subs]
    [re-frame.core :refer [dispatch subscribe]]
    [streamtide.ui.subs :as streamtide-subs]
    [streamtide.ui.components.connect-wallet :refer [connect-wallet-btn]]))


(def discord-invite-link "https://discord.com/invite/sS2AWYm")


(defn no-items-found [{:keys [:message :extra-classes] :or {message "No items found"}}]
  "Default list result when there are no items"
  [:div.no-items-found {:class (str/join " " extra-classes)}
   message])

(defn support-seal [{:keys [:main?]}]
  "Shows the Streamtide seal"
  [:div.support.d-none.d-lg-block
   [:img.item-day {:src (if main? "/img/layout/seal-dark.svg" "/img/layout/seal-lilac.svg" ) :alt "supporting people not platforms"}]
   [:img.item-night {:src "/img/layout/seal-light.svg" :alt "supporting people not platforms"}]])

(defn nav-anchor [{:keys [route params query] :as props} & childs]
  "Internal navigation link"
  (into [:a (merge {:on-click #(do
                                 (.preventDefault %)
                                 (when route (dispatch [::router-events/navigate route params query])))
                    :href (when route @(subscribe [:district.ui.router.subs/resolve route params query]))}
                   (dissoc props :route :params :query))]
        childs))

(defn sign-in-button [{:keys [:class]}]
  "Button to trigger the user's login or logout"
  (let [active-session? (subscribe [::streamtide-subs/active-account-has-session?])
        current-account (subscribe [::accounts-subs/active-account])]
    (fn []
      [:<>
       [:div.btLoginContainer
       (when @current-account
         (if @active-session?
           [:button.btLogin.btBasic.btBasic-light
            {:class class
             :on-click #(dispatch [:user/sign-out])}
            (str "Log out")]
           [:button.btLogin.btBasic.btBasic-light
            {:class class
             :on-click #(dispatch [:user/sign-in])}
            (str "WEB3 LOGIN")]))]
       [:div.connectWalletContainer
        {:class class}
        [connect-wallet-btn {:class "btLogin connectWallet"}]]])))
