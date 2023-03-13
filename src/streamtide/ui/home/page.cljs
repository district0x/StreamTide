(ns streamtide.ui.home.page
  "Home page"
  (:require
    [district.ui.component.page :refer [page]]
    [streamtide.ui.components.app-layout :refer [app-layout]]))


(defmethod page :route/home []
  (let []
    (fn []
      [app-layout
       [:main.pageSite
        {:id "home"}
        [:div.contentHome
         [:div.container
          [:h2.titleHome
           [:span "RIDE"]
           [:span.the [:span "the"]]
           [:span "STREAM"]]
          [:div.text
           [:p "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud."]]
          [:div.video
           [:video
            {:controls true}
            [:source {:src "/img/layout" :type "video/mp4"}]
            "Your browser does not support the video tag."]]]
         [:div.hero
          [:div.imgHero
           [:video.item-day
            {:autoPlay true :muted true :loop true}
            [:source {:src "/img/layout/stream_day.mp4" :type "video/mp4"}]
            "Your browser does not support the video tag."]
           [:video.item-night
            {:autoPlay true :muted true :loop true}
            [:source {:src "/img/layout/stream_night.mp4" :type "video/mp4"}]
            "Your browser does not support the video tag."]]]]]])))
