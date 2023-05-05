(ns streamtide.ui.components.general
  (:require
    [clojure.string :as str]
    [district.ui.router.events :as router-events]
    [re-frame.core :refer [dispatch subscribe]]
    [streamtide.ui.subs :as streamtide-subs]))


(defn no-items-found [extra-classes]
  "Default list result when there are no items"
  [:div.no-items-found {:class (str/join " " extra-classes)}
   "No items found"])

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

(defn sign-in-button []
  "Button to trigger the user's login or logout"
  (let []
    (fn []
      (let [active-session? (subscribe [::streamtide-subs/active-account-has-session?])]
        (if @active-session?
          [:a.btLogin.btBasic.btBasic-light.d-none.d-lg-inline-block
           {:on-click #(dispatch [:user/sign-out])}
           (str "Log out")]
          [:a.btLogin.btBasic.btBasic-light.d-none.d-lg-inline-block
           {:on-click #(dispatch [:user/sign-in])}
           (str "WEB LOGIN")])))))
