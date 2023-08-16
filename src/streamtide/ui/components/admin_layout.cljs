(ns streamtide.ui.components.admin-layout
  "Layouts component for rendering the admin page"
  (:require
    [district.ui.router.events :as router-events]
    [district.ui.router.subs :as router-subs]
    [re-frame.core :refer [subscribe dispatch]]
    [streamtide.ui.components.app-layout :refer [app-layout]]
    [streamtide.ui.components.general :refer [nav-anchor]]
    [streamtide.ui.utils :refer [check-session]]))


(def admin-nav-menu-items [{:text "Grant Approval Feeds"
                            :route :route.admin/grant-approval-feed}
                           {:text "Rounds"
                            :route :route.admin/rounds}
                           {:text "Black Listing"
                            :route :route.admin/black-listing}
                           {:text "Announcements"
                            :route :route.admin/announcements}])

(defn admin-nav-menu []
  "Menu for admin pages"
  (let [active-page (subscribe [::router-subs/active-page])]
    (fn []
      (check-session)
      [:<>
       [:div.selectAdmin-selected.d-lg-none
        [:select
         {:on-change (fn [item]
                       (let [val (-> item .-target .-value)]
                         (dispatch [::router-events/navigate (keyword val)])))
          :value (-> @(subscribe [::router-subs/active-page]) :name symbol str)}
         (doall (map-indexed
                  (fn [idx {:keys [:text :route]}]
                    [:option
                     {:key (str idx)
                      :value (str (symbol route))}
                     text])
                  admin-nav-menu-items))]]
       [:div.selectAdmin-items.select-hide
        (doall (map-indexed
                 (fn [idx {:keys [:text :route]}]
                   [nav-anchor (merge {:key (str idx) :route route}
                                      (when (= (:name @active-page) route) {:class "same-as-selected"}))
                    text])
                 admin-nav-menu-items))]])))

(defn admin-layout []
  "app container component for rendering admin pages. Shows everything app-layout shows plus the admin menu"
  (let []
    (fn [& children]
      [app-layout
       [:main.pageSite
        {:id "admin"}
        [:div.headerAdmin
         [:div.container
          [:h2.titlePage "Admin"]]]
        [:div.contentAdmin.container
         [:div.menuAdmin.inputField.simple
          [admin-nav-menu]]
         [:div.containerAdmin
          (map-indexed (fn [index item]
                         (with-meta item {:key (keyword "ca" index)}))
                       children)]]]])))
