(ns streamtide.ui.about.page
  "Page showing description of Streamtide"
  (:require
    [district.ui.component.page :refer [page]]
    [streamtide.ui.components.app-layout :refer [app-layout]]))

(defmethod page :route.my-settings-grants/index []
  (let []
    (fn []
      [app-layout
       [:main.pageSite
        {:id "about"}
        [:div
         [:div.headerGrants
          [:div.container
           [:h1.titlePage "About"]]]
         [:div.container
          "TODO"
          ]]]])))
