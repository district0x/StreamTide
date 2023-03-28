(ns streamtide.ui.components.app-layout
  "Main layouts components for rendering the page"
  (:require
    [district.graphql-utils :as gql-utils]
    [district.ui.graphql.subs :as gql]
    [district.ui.router.subs :as router-subs]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]
    [streamtide.ui.components.announcement :refer [announcement]]
    [streamtide.ui.components.general :refer [nav-anchor sign-in-button support-seal]]
    [streamtide.ui.events :as st-events]
    [streamtide.ui.subs :as st-subs]))


(def nav-menu-items [{:text "Grants"
                      :route :route.grants/index}
                     {:text "Leaderboard"
                      :route :route.leaderboard/index}
                     {:text "About"
                      :route :route.my-settings-grants/index}
                     {:text  "My Settings"
                      :route :route.my-settings/index
                      :require-session? true}
                     {:text  "Admin"
                      :route :route.admin/grant-approval-feed
                      :roles [:role/admin]}])

; TODO maybe move this to a common ns?
(defn build-get-roles-query []
  [:roles])

(defn nav-menu []
  "Navigation menu of the different pages of the website"
  (let [active-page (subscribe [::router-subs/active-page])
        active-session? (subscribe [::st-subs/active-account-has-session?])]
    (fn []
      (let [user-roles-query (when @active-session? (subscribe [::gql/query {:queries [(build-get-roles-query)]}]))
            user-roles (when user-roles-query (->> @user-roles-query :roles (map gql-utils/gql-name->kw)))]
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
                            (let [required-roles (:roles nav-menu-item)]
                              (or (not required-roles)
                                  (some (set user-roles) required-roles)))
                            ) nav-menu-items)))]))))

(defn header []
  "Common header for all the pages. Shows the navigation menu, login button and so"
  (let [day-night (subscribe [::st-subs/day-night-switch])]
    (fn []
  [:header
   {:id "headerSite"}
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
       [nav-anchor {:class "btCar" :route :route.send-support/index} ]
       [sign-in-button]]]]
   ;[:nav.menuMobile "TODO"]
   ])))


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
      [:div.app-container {:id "app-container"
                           :class (str "theme-" @day-night " " @day-night)}
       [header]
       (map-indexed (fn [index item]
                      (with-meta item {:key (keyword "c" index)}))
                    children)
       [footer]])))
