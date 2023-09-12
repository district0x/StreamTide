(ns streamtide.ui.components.app-layout
  "Main layouts components for rendering the page"
  (:require
    [district.graphql-utils :as gql-utils]
    [district.ui.component.notification :as notification]
    [district.ui.graphql.subs :as gql]
    [district.ui.router.subs :as router-subs]
    [district.ui.web3-accounts.subs :as accounts-subs]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]
    [streamtide.ui.components.announcement :refer [announcement]]
    [streamtide.ui.components.grant-approved-notification :refer [grant-approved-notification]]
    [streamtide.ui.components.general :refer [nav-anchor sign-in-button support-seal discord-invite-link]]
    [streamtide.ui.events :as st-events]
    [streamtide.ui.subs :as st-subs]
    [streamtide.ui.utils :refer [build-grant-status-query]]))


(def nav-menu-items [{:text "Creators"
                      :route :route.grants/index}
                     {:text "Leaderboard"
                      :route :route.leaderboard/index}
                     {:text "About"
                      :route :route.about/index}
                     {:text  "My Content"
                      :route :route.my-content/index
                      :require-grant? true}
                     {:text  "My Settings"
                      :route :route.my-settings/index
                      :require-session? true}
                     {:text  "Admin"
                      :route :route.admin/grant-approval-feed
                      :roles [:role/admin]}])

(defn build-get-roles-query []
  [:roles])

(defn nav-menu []
  "Navigation menu of the different pages of the website"
  (let [active-page (subscribe [::router-subs/active-page])
        active-session? (subscribe [::st-subs/active-account-has-session?])
        active-account (subscribe [::accounts-subs/active-account])]
    (fn []
      (let [user-roles-query (when @active-session? (subscribe [::gql/query {:queries [(build-get-roles-query)]}]))
            user-roles (when user-roles-query (->> @user-roles-query :roles (map gql-utils/gql-name->kw)))
            grant-status-query (when (and @active-account @active-session?)
                                 (subscribe [::gql/query {:queries [(build-grant-status-query {:user/address @active-account})]}]))
            grant-status (when grant-status-query (-> @grant-status-query :grant :grant/status gql-utils/gql-name->kw))]
        [:ul.contentMenu
         (doall (map-indexed
                  (fn [idx {:keys [:text :route :require-session?]}]
                    (let [disabled (and require-session? (not @active-session?))]
                      [:li {:key (str idx)}
                       [nav-anchor {:route (when (not disabled) route)
                                    :class (cond-> ""
                                                   (= (:name @active-page) route) (str "selected")
                                                   disabled (str " disabled"))}
                        text]]))
                  (filter (fn [nav-menu-item]
                            (let [required-roles (:roles nav-menu-item)
                                  require-grant? (:require-grant? nav-menu-item)]
                              (or (and (not required-roles) (not require-grant?))
                                  (and required-roles (some (set user-roles) required-roles))
                                  (and require-grant? (= grant-status :grant.status/approved))))
                            ) nav-menu-items)))]))))

(defn header []
  "Common header for all the pages. Shows the navigation menu, login button and so"
  (let [day-night (subscribe [::st-subs/day-night-switch])
        show-mobile-menu? (subscribe [::st-subs/menu-mobile-switch])
        cart-full? (subscribe [::st-subs/cart-full?])]
    (fn []
  [:header
   {:id "headerSite"
    :class (when @show-mobile-menu? "open")}
    [announcement]
    [:div.contentHeader
     [:div.container
      [:h1.logoSite
       [nav-anchor {:route :route/home} "StreamTide"]]
      [:nav.menuSite
       [nav-menu]]
      [:div.btsMenu
       [:a.btToggle.js-btToggle {:data-theme @day-night
                                 :on-click (fn [e]
                                             (.stopPropagation e)
                                             (dispatch [::st-events/day-night-switch]))}
        [:img {:src "/img/layout/icon-moon.svg" :alt "icon-night"}]
        [:img {:src "/img/layout/icon-sun.svg" :alt "icon-day"}]]
       [nav-anchor {:class (str "btCar " (when @cart-full? "full"))
                    :route :route.send-support/index}]
       [:button.btMenu.d-lg-none.js-btMenu {:on-click(fn [e]
                                                        (.stopPropagation e)
                                                        (dispatch [::st-events/menu-mobile-switch]))} "Menu"]
       [sign-in-button {:class "d-none d-lg-inline-block"}]]]]
   [:nav.menuMobile
    [nav-menu]
    [sign-in-button {:class "d-lg-none"}]]])))


(defn footer []
  "Common footer for all the pages"
  (let [hide? (r/atom false)]
    (fn []
      [:footer
       {:id "footerSite"
        :class (when @hide? "close")}
       [:div.container
        [:div.contentFooter
         [:button.btFooter.js-btFooter
          {:on-click #(reset! hide? true)}
          "Close"]
         [support-seal {:main? true}]
         [:div.form.formNewsletter {:action nil}
          [:input {:id "#email" :type "email" :name "email" :placeholder "Your Email" :required true}]
          [:button.btBasic.btBasic-dark {:type "submit"} "GET UPDATES"]]]]])))


(defn app-layout []
  "Main app container. Shows the header, menu and footer and includes the children specified as args"
  (let [day-night (subscribe [::st-subs/day-night-switch])]
    (fn [& children]
      (let [announcement? (some? (announcement))]
        [:div.app-container {:id "app-container"
                             :class (str "theme-" @day-night " " @day-night)}
         [header]
         (when announcement?
           [:div.announcement-gap])
         (map-indexed (fn [index item]
                        (with-meta
                          (if (= 0 index) (update item 1 update :class str " closeFooter") item) ; TODO remove this when restoring the footer
                          {:key (keyword "c" index)}))
                      children)
         ;[footer]
         [:a.discord-button {:href discord-invite-link :rel "noreferrer noopener"}
          [:img.discord-image {:src "/img/layout/ico_discord.svg" :alt "Discord"}]]
         [grant-approved-notification (when announcement? {:class "has-announcement"})]
         [notification/notification]]))))
