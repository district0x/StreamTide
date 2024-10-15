(ns streamtide.ui.components.media-embed
  "Embed media from different popular sites.
  It uses some regex to identify the website and re-format the url to embed the media (image/video) in the current page"
  (:require
    [clojure.string :as string]
    [district.ui.component.form.input :refer [checkbox-input]]
    [reagent.core :as r]
    [re-frame.core :refer [dispatch subscribe]]
    [streamtide.ui.components.general :refer [discord-invite-link]]
    [streamtide.ui.config :as config]
    [streamtide.ui.events :as st-events]
    [streamtide.ui.subs :as st-subs]
    [streamtide.shared.utils :as shared-utils]))

(def supported-video-exts #{"mp4", "webm", "ogg"})
(def supported-audio-exts #{"mp3", "wav", "ogg", "m4a", "aac", "webm"})


;; Pattern and replacements adjusted from nodebb-plugin-ns-embed
(def matching-rules
  (let [domain (or (:domain config/config-map) (-> js/window .-location .-hostname))]
    [; YouTube
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?(?:youtube\\.com\\/(?!user|channel)\\S*(?:(?:\\/e(?:mbed))?\\/|watch\\?(?:\\S*?&?v\\=))|youtu\\.be\\/)([a-zA-Z0-9_-]{6,11})")
      :replacement "https://www.youtube.com/embed/$1"
      :iframe-params {:width 560
                      :height 315
                      :allow "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                      :title "YouTube video player"}
      :type #{:video}}
     ; Twitch
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?twitch\\.tv\\/.*\\/v\\/(\\d+)")
      :replacement (str "https://player.twitch.tv/?autoplay=false&video=v$1&parent=" domain)
      :iframe-params {:width 620
                      :height 378}
      :type #{:video}}
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?twitch\\.tv\\/videos\\/(\\d+)")
      :replacement (str "https://player.twitch.tv/?autoplay=false&video=v$1&parent=" domain)
      :iframe-params {:width 620
                      :height 378}
      :type #{:video}}
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?twitch\\.tv(?!.*\\/v\\/)\\/([a-zA-Z0-9_-]+)")
      :replacement (str "https://player.twitch.tv/?autoplay=false&video=v$1&parent=" domain)
      :iframe-params {:width 620
                      :height 378}
      :type #{:video}}
     ; Vimeo
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?vimeo\\.com\\/(?:channels\\/(?:\\w+\\/)?|groups\\/(?:[^\\/]*)\\/videos\\/|)(\\d+)(?:|\\/\\?)")
      :replacement "https://player.vimeo.com/video/$1"
      :type #{:video}}
     ; Vine
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?vine\\.co\\/v\\/([a-zA-Z0-9_-]{6,11})")
      :replacement "https://vine.co/v/$1/embed/simple"
      :type #{:video}}
     ; coub
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?coub\\.com\\/view\\/([a-zA-Z0-9_-]{4,11})")
      :replacement "https://coub.com/embed/$1?muted=false&autostart=false&originalSize=false&hideTopBar=true&startWithHD=true"
      :type #{:video}}
     ; Blender Tube
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?video\\.blender\\.org\\/videos\\/watch\\/([a-zA-Z0-9_-]{4,36})")
      :replacement "//video.blender.org/videos/embed/$1"
      :type #{:video}}
     ; Dailymotion
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?dailymotion\\.com\\/video\\/([a-zA-Z0-9_-]{4,11})")
      :replacement "//www.dailymotion.com/embed/video/$1"
      :type #{:video}}
     ; FramaTube
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?framatube\\.org\\/videos\\/watch\\/([a-zA-Z0-9_-]{4,36})")
      :replacement "//framatube.org/videos/embed/$1"
      :type #{:video}}
     ; MixCloud
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?mixcloud\\.com\\/([a-zA-Z0-9_-]{4,36})\\/([a-zA-Z0-9_-]{4,136})")
      :replacement "https://www.mixcloud.com/widget/iframe/?light=1&hide_artwork=1&feed=%2F$1%2F$2%2F"
      :type #{:audio :other}}
     ; SoundCloud
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?soundcloud\\.com\\/([a-zA-Z0-9_^/-]{4,250})")
      :replacement "https://w.soundcloud.com/player/?url=https://soundcloud.com/$1&color=%23ff5500&auto_play=false&hide_related=false&show_comments=true&show_user=true&show_reposts=false&show_teaser=true&visual=true"
      :type #{:audio :other}}
     ; Spotify
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:www\\.)?open\\.spotify\\.com\\/(album|track|user|artist|playlist)\\/([a-zA-Z0-9_-]{4,36})")
      :replacement "https://open.spotify.com/embed/$1/$2"
      :iframe-params {:height 160}
      :type #{:audio :other}}
     ; Twitter
     {:regex (re-pattern "(?:https?:\\/\\/)?(?:twitter\\.com)\\/([^\\/\"\\s]*)\\/statuse?s?\\/([^\\/\"\\s\\?]*)(\\/photo\\/\\d|\\?.*|)")
      :replacement "https://platform.twitter.com/embed/Tweet.html?id=$2"
      :iframe-params {:height 450}
      :type #{:audio :video :image :other}}]))

(defn- file-ext [filename]
  (string/lower-case (last (string/split filename "."))))

(defn- supported-video-ext? [ext]
  (contains? supported-video-exts ext))

(defn- supported-audio-ext? [ext]
  (contains? supported-audio-exts ext))

(def current-url (r/atom nil))
(def show-popup? (r/atom false))

(defn safe-link-popup []
  (let [form-data (r/atom {})
        close-popup (fn [e]
                      (when e
                        (.stopPropagation e))
                      (reset! show-popup? false)
                      (reset! form-data {}))]
    (reset! show-popup? false)
    (fn []
      (let [checked? (:trust-domain @form-data)
            domain (when @current-url (shared-utils/url->domain @current-url))]
        [:div {:id "popUpSafeLink" :style {"display" (if @show-popup? "flex" "none")}}
         [:div.bgPopUp {:on-click #(close-popup %)}]
         [:div.popUpSafeLink
          [:button.btClose {:on-click #(close-popup %)} "Close" ]
          [:div.content
           [:h3 "Open external website"]
           [:p "This link will take you to"]
           [:pre @current-url]
           [:p "Are you sure you want to go there?"]
           [:div.form
            [:label.checkField.simple
             [checkbox-input {:id :trust-domain
                              :form-data form-data}]
             [:span.checkmark {:class (when checked? "checked")}]
             [:span.text "Always trust domain "
              [:span.domain domain]]]
            [:a.btBasic.btBasic-light.visit
             {:href @current-url
              :target "_blank"
              :rel "noopener noreferrer"
              :on-click (fn []
                          (close-popup nil)
                          (when checked?
                            (dispatch [::st-events/trust-domain {:domain domain}])))} "Visit Site"]
            [:button.btBasic.cancel {:on-click #(close-popup %)} "Cancel"]]
           [:p.footnote "Found suspicious link? Please report it to our "
            [:a {:href discord-invite-link :target :_blank :rel "noreferrer noopener"} "Discord server"]]]]]))))

(defn safe-external-link [url {:keys [:disable-safe? :class :text]}]
  (let [domain (shared-utils/url->domain url)
        trust-domain? (subscribe [::st-subs/trust-domain? domain])]
    (fn []
      [:a.external-link
       (merge {:href   url
               :target "_blank"
               :rel    "noopener noreferrer"
               :class class}
              (when (and (not disable-safe?) (not @trust-domain?))
                {:on-click (fn [e]
                             (.preventDefault e)
                             (reset! current-url url)
                             (reset! show-popup? true))}))
       (if text text url)])))

(defn embed-url [url type]
  "If the URL is from a popular site, it embedded it directly in the page"
  (let [{:keys [:regex :replacement :iframe-params :component :replacement-target]} (first (filter (and
                                                                                                     #(contains? (:type %) type)
                                                                                                     #(re-find (:regex %) url)) matching-rules))
        src (when regex (string/replace url regex replacement))]
    (when src
      (if component
        (assoc-in component replacement-target src)
        [:iframe
         (merge
           {:width 560
            :height 315
            :src src
            :scrolling "no"
            :frameBorder "0"
            :allowFullScreen true}
           iframe-params)]))))

(defn embed-video [url safe-link-opts]
  "Embed an external video in the current page"
  (if-let [src (embed-url url :video)]
    src
    (let [ext (file-ext url)]
      (if (supported-video-ext? ext)
        [:video {:controls true}
         [:source {:src url :type (str "video/" ext)}]
         "Your browser does not support the video tag."]
        [safe-external-link url safe-link-opts]))))

(defn embed-image [url safe-link-opts]
  "Embed an external image in the current page"
  (if-let [src (embed-url url :image)]
    src
    [:img {:src url}]))

(defn embed-audio [url safe-link-opts]
  "Embed an external audio in the current page"
  (if-let [src (embed-url url :audio)]
    src
    (let [ext (file-ext url)]
      (if (supported-audio-ext? ext)
        [:audio {:controls "1" :src url}]
        [safe-external-link url safe-link-opts]))))

(defn embed-other [url safe-link-opts]
  "Embed an external URL in the current page"
  (if-let [src (embed-url url :other)]
    src
    [safe-external-link url safe-link-opts]))
