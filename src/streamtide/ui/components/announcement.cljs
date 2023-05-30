(ns streamtide.ui.components.announcement
  (:require
    [district.ui.graphql.subs :as gql]
    [re-frame.core :refer [subscribe dispatch]]))

; TODO currently it takes the first announcement. Maybe it should show all?

(defn build-announcement-query []
  (let []
    [:announcements
     {:first 1}
     [[:items [:announcement/text]]]]))

(defn announcement []
  "Show the announcement defined in the server, if any"
  (let [announcement-query (subscribe [::gql/query {:queries [(build-announcement-query)]}])
        announcement-text (-> @announcement-query :announcements :items first :announcement/text)]
    (when announcement-text
      [:div.anuncio.d-none.d-lg-block
       [:p announcement-text]])))
