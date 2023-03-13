(ns streamtide.ui.components.admin-layout
  "Layouts component for rendering the admin page"
  (:require
    [district.ui.router.subs :as router-subs]
    [re-frame.core :refer [subscribe dispatch]]
    [streamtide.ui.components.app-layout :refer [app-layout]]
    [streamtide.ui.components.general :refer [nav-anchor]]))


(def admin-nav-menu-items [{:text "Grant Approval Feeds"
                            :route :route.admin/grant-approval-feed}
                           {:text "Black Listing"
                            :route :route.admin/black-listing}
                           {:text "Announcements"
                            :route :route.admin/announcements}])

(defn admin-nav-menu []
  "Menu for admin pages"
  (let [active-page (subscribe [::router-subs/active-page])]
    (fn []
      [:div.selectAdmin-items.select-hide
       (doall (map-indexed
                (fn [idx {:keys [:text :route]}]
                  [nav-anchor (merge {:key (str idx) :route route}
                                    (when (= (:name @active-page) route) {:class "same-as-selected"}))
                   text])
                admin-nav-menu-items))])))

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
