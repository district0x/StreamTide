(ns streamtide.ui.components.infinite-scroll-masonry
  (:require
    [cljsjs.masonry]
    [cljsjs.react-infinite-scroll]
    [reagent.core :as r]
    ))

(def masonry (r/adapt-react-class js/masonry))
(def scroller (r/adapt-react-class js/infinitescroll))

(defn infinite-scroll-masonry [props & [children]]
  "Infinite scroll based on react-infinite-scroller with masonry using react-masonry-component.
  It shows elements in a masonry layout, and loads more element when scrolling to the bottom"
  (fn [props & [children]]
    (let [{:keys [:class
                  :use-window-as-scroll-container
                  :loading-spinner-delegate
                  :load-fn
                  :loading?
                  :has-more?]
           :or {class ""
                use-window-as-scroll-container true
                loading-spinner-delegate (fn [] [:div.loading-spinner "Loading..."])}} props]
      [scroller {:hasMore has-more?
                 :useWindow use-window-as-scroll-container
                 :loadMore #(when-not loading? (load-fn))}
       [:<>
        [masonry {:className class}
         children]
        (when loading? (loading-spinner-delegate))]])))
