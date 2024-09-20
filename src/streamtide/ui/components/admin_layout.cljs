(ns streamtide.ui.components.admin-layout
  "Layouts component for rendering the admin page"
  (:require
    [district.ui.router.events :as router-events]
    [district.ui.router.subs :as router-subs]
    [re-frame.core :refer [subscribe dispatch]]
    [streamtide.ui.components.app-layout :refer [app-layout]]
    [streamtide.ui.components.general :refer [nav-anchor]]
    [streamtide.ui.utils :refer [check-session]]
    [streamtide.ui.components.custom-select :refer [react-select]]))


(def admin-nav-menu-items [{:text "Grant Approval Feeds"
                            :route :route.admin/grant-approval-feed}
                           {:text "Rounds"
                            :route :route.admin/rounds}
                           {:text "Black Listing"
                            :route :route.admin/black-listing}
                           {:text "Announcements"
                            :route :route.admin/announcements}
                           {:text "Campaigns"
                            :route :route.admin/campaigns}])

(defn admin-nav-menu []
  "Menu for admin pages"
  (let [active-page (subscribe [::router-subs/active-page])]
    (fn []
      (check-session)
      [:<>
       [:div.custom-select.selectForm.d-lg-none
        (let [options (doall (map
                               (fn [{:keys [:text :route]}]
                                 {:value (str (symbol route))
                                  :label text})
                               admin-nav-menu-items))]
        [react-select
         {:class "options"
          :on-change (fn [selected-entry]
                       (let [val (.-value selected-entry)]
                         (dispatch [::router-events/navigate (keyword val)])))
          :value (let [value (-> @(subscribe [::router-subs/active-page]) :name symbol str)
                       label (:label (first (filter #(= value (:value %)) options)))]
                   {:value value :label label})
          :options options}])]
       [:div.admin-items.select-hide.inputField
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
         [:div.menuAdmin
          [admin-nav-menu]]
         [:div.containerAdmin
          (map-indexed (fn [index item]
                         (with-meta item {:key (keyword "ca" index)}))
                       children)]]]])))
