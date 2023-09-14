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
           [:span "FUEL"]
           [:span.the [:span "the"]]
           [:span "FUTURE"]]
          [:div.text
           [:p "Stream Tide is an open-source patronage tool that operates on Web3 and microgrants. We host grant matching events that match donations made to creators, turning 'a small stream of support into a tidal wave of support 🌊'. \n Our goal is to support creators, promote open-source, and the creative commons, helping to shape the decentralized future of work. 🚀" ]]]
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
